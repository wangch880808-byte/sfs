package cn.nus.zknu.module.edu.service.aireview;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduGenerateReqDTO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_ANALYSIS_NO_STUDENTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_ANALYSIS_SCOPE_INVALID;

@Service
@Slf4j
public class AiEduAnalysisServiceImpl implements AiEduAnalysisService {

    @Resource
    private AiEduReviewApi aiEduReviewApi;
    @Resource
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduClassStudentMapper classStudentMapper;
    @Resource
    private EduClassMapper classMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduAiBusinessAccessService accessService;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduMetricPolicy metricPolicy;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private StatisticsMapper statisticsMapper;

    @Override
    public Flux<String> analyzeClassScoreStream(Long classId, Long courseId, Long homeworkId) {
        ClassAnalysisContext context = buildClassAnalysisContext(classId, courseId, homeworkId);
        AiEduGenerateReqDTO req = new AiEduGenerateReqDTO();
        req.setType("analysis_class");
        req.setStudentData(context.payload());
        req.setBusinessObjectId("class:" + classId + ":course:" + courseId + ":homework:" + homeworkId);
        return aiEduReviewApi.generateCommentStream(req);
    }

    private ClassAnalysisContext buildClassAnalysisContext(Long classId, Long courseId, Long homeworkId) {
        validateAnalysisScope(courseId, classId);
        EduClassDO clazz = classMapper.selectById(classId);
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        List<EduClassStudentDO> classStudents = classStudentMapper.selectListByClassId(classId);
        List<EduHomeworkDO> homeworks = homeworkMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkDO>()
                        .eq(EduHomeworkDO::getCourseId, courseId)
                        .eq(EduHomeworkDO::getClassId, classId)
                        .eq(EduHomeworkDO::getId, homeworkId)
                        .in(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED, EduHomeworkStatusEnum.CLOSED,
                                EduHomeworkStatusEnum.ARCHIVED)
                        .orderByAsc(EduHomeworkDO::getCreateTime));

        int studentCount = classStudents.size();
        if (studentCount <= 0) {
            throw exception(AI_REVIEW_ANALYSIS_NO_STUDENTS);
        }
        int homeworkCount = homeworks.size();
        if (homeworkCount <= 0) {
            throw exception(AI_ANALYSIS_SCOPE_INVALID);
        }

        if (dataScopeService.isPublicCourse(courseId)) {
            // 新认领人不能借班级研判读取原教师任务的成绩，任务仍按布置者授权。
            homeworks.forEach(accessService::validateHomeworkViewAccess);
        }
        List<Long> homeworkIds = homeworks.stream().map(EduHomeworkDO::getId).toList();
        List<EduHomeworkRecordDO> records = homeworkIds.isEmpty() ? List.of() : homeworkRecordMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .in(EduHomeworkRecordDO::getHomeworkId, homeworkIds)
                        .in(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.SUBMITTED,
                        EduHomeworkRecordStatusEnum.CORRECTED, EduHomeworkRecordStatusEnum.REDO,
                        EduHomeworkRecordStatusEnum.RESUBMITTED));
        Map<Long, Set<Long>> assigned = new HashMap<>();
        assigneeMapper.selectListByHomeworkIds(homeworkIds).forEach(item ->
                assigned.computeIfAbsent(item.getHomeworkId(), ignored -> new HashSet<>()).add(item.getStudentId()));
        List<EduHomeworkRecordDO> allRecords = records.stream()
                .filter(record -> assigned.getOrDefault(record.getHomeworkId(), Set.of()).contains(record.getStudentId()))
                .toList();

        Map<Long, String> userMap = buildStudentAliasMap(classStudents);
        Map<Long, EduHomeworkDO> homeworkMap = homeworks.stream()
                .collect(Collectors.toMap(EduHomeworkDO::getId, homework -> homework));
        Map<Long, List<EduHomeworkRecordDO>> recordsByHomework = allRecords.stream()
                .collect(Collectors.groupingBy(EduHomeworkRecordDO::getHomeworkId, LinkedHashMap::new, Collectors.toList()));

        List<EduHomeworkRecordDO> gradedRecords = allRecords.stream()
                .filter(record -> Integer.valueOf(EduHomeworkRecordStatusEnum.CORRECTED).equals(record.getStatus()) && record.getScore() != null)
                .toList();
        long returnedCount = allRecords.stream()
                .filter(record -> Integer.valueOf(EduHomeworkRecordStatusEnum.REDO).equals(record.getStatus()))
                .count();
        long resubmittedPendingCount = allRecords.stream()
                .filter(record -> Integer.valueOf(EduHomeworkRecordStatusEnum.RESUBMITTED).equals(record.getStatus()))
                .count();
        long pendingCount = allRecords.stream()
                .filter(record -> record.getStatus() != null
                        && EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(record.getStatus()))
                .count();

        int expectedRecordCount = homeworks.stream().mapToInt(hw -> (int) metricPolicy.expectedStudents(hw.getId())).sum();
        int submittedCount = allRecords.size();
        int gradedCount = gradedRecords.size();
        int unsubmittedCount = Math.max(expectedRecordCount - submittedCount, 0);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("avgScore", "0.0");
        overview.put("maxScore", 0);
        overview.put("minScore", 0);
        overview.put("passCount", 0);
        overview.put("passRate", "0.0%");
        overview.put("scoreDistribution", buildEmptyScoreDistribution());
        if (!gradedRecords.isEmpty()) {
            double avg = gradedRecords.stream()
                    .mapToInt(EduHomeworkRecordDO::getScore)
                    .average()
                    .orElse(0);
            int max = gradedRecords.stream().mapToInt(EduHomeworkRecordDO::getScore).max().orElse(0);
            int min = gradedRecords.stream().mapToInt(EduHomeworkRecordDO::getScore).min().orElse(0);
            long passCount = gradedRecords.stream()
                    .filter(record -> {
                        EduHomeworkDO homework = homeworkMap.get(record.getHomeworkId());
                        int passScore = metricPolicy.passScore(homework);
                        return record.getScore() >= passScore;
                    })
                    .count();
            overview.put("avgScore", formatDecimal(avg));
            overview.put("maxScore", max);
            overview.put("minScore", min);
            overview.put("passCount", passCount);
            overview.put("passRate", formatRate((int) passCount, gradedRecords.size()));
            overview.put("scoreDistribution", buildScoreDistribution(gradedRecords, homeworkMap));
        }

        List<Map<String, Object>> homeworkSummaries = buildHomeworkSummaries(homeworks, recordsByHomework, userMap, studentCount);
        Map<String, Object> studentSnapshots = buildStudentSnapshots(classStudents, allRecords, userMap, homeworkCount);
        String analysisPayload = buildClassAnalysisPayload(
                course != null ? course.getCourseName() : "",
                course != null ? course.getIndicatorName() : "",
                "",
                clazz != null ? clazz.getClassName() : "",
                studentCount,
                homeworkCount,
                expectedRecordCount,
                submittedCount,
                gradedCount,
                pendingCount,
                returnedCount,
                resubmittedPendingCount,
                unsubmittedCount,
                formatRate(submittedCount, expectedRecordCount),
                formatRate(gradedCount, expectedRecordCount),
                overview,
                homeworkSummaries,
                studentSnapshots);
        log.info("[analyzeClassScore] classId={}, courseId={}, homeworkId={}, studentCount={}, payloadLength={}",
                classId, courseId, homeworkId, studentCount, analysisPayload.length());
        return new ClassAnalysisContext(studentCount, homeworkCount, analysisPayload);
    }

    private void validateAnalysisScope(Long courseId, Long classId) {
        // 只读研判沿用原授权，不能借用要求班课在用的写入入口，否则封存历史会被挡住。
        if (dataScopeService.isPublicCourse(courseId)) {
            dataScopeService.validateClassCourseAccess(classId, courseId);
        } else {
            dataScopeService.validateCourseAccess(courseId);
            dataScopeService.validateClassAccess(classId);
        }
        if (!statisticsMapper.selectAnalysisClassCourseExists(courseId, classId)) {
            throw exception(AI_ANALYSIS_SCOPE_INVALID);
        }
    }

    private Map<Long, String> buildStudentAliasMap(List<EduClassStudentDO> classStudents) {
        List<Long> studentIds = classStudents.stream()
                .map(EduClassStudentDO::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Map<Long, String> aliases = new HashMap<>();
        for (int index = 0; index < studentIds.size(); index++) {
            aliases.put(studentIds.get(index), "学生" + (index + 1));
        }
        return aliases;
    }

    private String formatDecimal(double value) {
        return String.format("%.1f", value);
    }

    private String formatRate(int numerator, int denominator) {
        if (denominator <= 0) {
            return "0.0%";
        }
        return String.format("%.1f%%", numerator * 100.0 / denominator);
    }

    private Map<String, Integer> buildScoreDistribution(List<EduHomeworkRecordDO> records,
                                                        Map<Long, EduHomeworkDO> homeworkMap) {
        Map<String, Integer> distribution = buildEmptyScoreDistribution();
        for (EduHomeworkRecordDO record : records) {
            EduHomeworkDO homework = homeworkMap.get(record.getHomeworkId());
            if (homework == null) continue;
            String key = switch (metricPolicy.scoreBand(record.getScore(), metricPolicy.passScore(homework), homework.getScore())) {
                case UNQUALIFIED -> "unqualified";
                case QUALIFIED -> "qualified";
                case GOOD -> "good";
                case EXCELLENT -> "excellent";
            };
            distribution.merge(key, 1, Integer::sum);
        }
        return distribution;
    }

    private Map<String, Integer> buildEmptyScoreDistribution() {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("unqualified", 0);
        distribution.put("qualified", 0);
        distribution.put("good", 0);
        distribution.put("excellent", 0);
        return distribution;
    }

    private List<Map<String, Object>> buildHomeworkSummaries(List<EduHomeworkDO> homeworks,
                                                             Map<Long, List<EduHomeworkRecordDO>> recordsByHomework,
                                                             Map<Long, String> userMap,
                                                             int studentCount) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduHomeworkDO homework : homeworks) {
            List<EduHomeworkRecordDO> homeworkRecords = recordsByHomework.getOrDefault(homework.getId(), List.of());
            List<EduHomeworkRecordDO> gradedRecords = homeworkRecords.stream()
                    .filter(record -> Integer.valueOf(EduHomeworkRecordStatusEnum.CORRECTED).equals(record.getStatus()) && record.getScore() != null)
                    .sorted(Comparator.comparing(EduHomeworkRecordDO::getScore))
                    .toList();
            long pendingCount = homeworkRecords.stream()
                    .filter(record -> record.getStatus() != null
                        && EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(record.getStatus()))
                    .count();
            long returnedCount = homeworkRecords.stream()
                    .filter(record -> Integer.valueOf(EduHomeworkRecordStatusEnum.REDO).equals(record.getStatus()))
                    .count();
            long passCount = gradedRecords.stream()
                    .filter(record -> record.getScore() >= metricPolicy.passScore(homework))
                    .count();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("homeworkId", homework.getId());
            item.put("homeworkName", homework.getHomeworkName());
            item.put("semesterName", homework.getSemesterName());
            item.put("deadline", homework.getDeadline());
            item.put("submittedCount", homeworkRecords.size());
            item.put("gradedCount", gradedRecords.size());
            item.put("pendingCount", pendingCount);
            item.put("returnedCount", returnedCount);
            int expected = (int) metricPolicy.expectedStudents(homework.getId());
            item.put("unsubmittedCount", Math.max(expected - homeworkRecords.size(), 0));
            item.put("submissionRate", formatRate(homeworkRecords.size(), expected));
            item.put("avgScore", gradedRecords.isEmpty()
                    ? "0.0"
                    : formatDecimal(gradedRecords.stream().mapToInt(EduHomeworkRecordDO::getScore).average().orElse(0)));
            item.put("passRate", formatRate((int) passCount, gradedRecords.size()));
            item.put("riskScore", calculateHomeworkRiskScore(
                    homeworkRecords.size(),
                    expected,
                    gradedRecords.stream().mapToDouble(EduHomeworkRecordDO::getScore).average().orElse(0),
                    pendingCount,
                    Math.max(expected - homeworkRecords.size(), 0)));

            if (!gradedRecords.isEmpty()) {
                EduHomeworkRecordDO lowest = gradedRecords.get(0);
                EduHomeworkRecordDO highest = gradedRecords.get(gradedRecords.size() - 1);
                item.put("highestStudent", resolveStudentName(userMap, highest.getStudentId()));
                item.put("highestScore", highest.getScore());
                item.put("lowestStudent", resolveStudentName(userMap, lowest.getStudentId()));
                item.put("lowestScore", lowest.getScore());
            }
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildStudentSnapshots(List<EduClassStudentDO> classStudents,
                                                      List<EduHomeworkRecordDO> allRecords,
                                                      Map<Long, String> userMap,
                                                      int homeworkCount) {
        Map<Long, List<EduHomeworkRecordDO>> recordsByStudent = allRecords.stream()
                .collect(Collectors.groupingBy(EduHomeworkRecordDO::getStudentId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduClassStudentDO classStudent : classStudents) {
            List<EduHomeworkRecordDO> studentRecords = recordsByStudent.getOrDefault(classStudent.getStudentId(), List.of());
            List<EduHomeworkRecordDO> gradedRecords = studentRecords.stream()
                    .filter(record -> Integer.valueOf(EduHomeworkRecordStatusEnum.CORRECTED).equals(record.getStatus()) && record.getScore() != null)
                    .toList();
            double avgScore = gradedRecords.stream()
                    .mapToInt(EduHomeworkRecordDO::getScore)
                    .average()
                    .orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", classStudent.getStudentId());
            item.put("studentName", resolveStudentName(userMap, classStudent.getStudentId()));
            item.put("submittedCount", studentRecords.size());
            item.put("gradedCount", gradedRecords.size());
            item.put("missingCount", Math.max(homeworkCount - studentRecords.size(), 0));
            item.put("avgScore", formatDecimal(avgScore));
            rows.add(item);
        }

        List<Map<String, Object>> topStudents = rows.stream()
                .sorted(Comparator.comparingDouble(this::extractAvgScore).reversed()
                        .thenComparingInt(this::extractMissingCount))
                .limit(5)
                .toList();
        List<Map<String, Object>> bottomStudents = rows.stream()
                .sorted(Comparator.comparingDouble(this::extractAvgScore)
                        .thenComparing(Comparator.comparingInt(this::extractMissingCount).reversed()))
                .limit(5)
                .toList();
        List<Map<String, Object>> missingSubmissionStudents = rows.stream()
                .filter(item -> extractMissingCount(item) > 0)
                .sorted(Comparator.comparingInt(this::extractMissingCount).reversed()
                        .thenComparingDouble(this::extractAvgScore))
                .limit(5)
                .toList();

        Map<String, Object> snapshots = new LinkedHashMap<>();
        snapshots.put("topStudents", topStudents);
        snapshots.put("bottomStudents", bottomStudents);
        snapshots.put("missingSubmissionStudents", missingSubmissionStudents);
        return snapshots;
    }

    private int calculateHomeworkRiskScore(int submittedCount, int studentCount, double avgScore,
                                           long pendingCount, int unsubmittedCount) {
        double missingRate = studentCount <= 0 ? 0 : unsubmittedCount * 100.0 / studentCount;
        double submitRate = studentCount <= 0 ? 0 : submittedCount * 100.0 / studentCount;
        double pendingRate = studentCount <= 0 ? 0 : pendingCount * 100.0 / studentCount;
        double risk = (100 - avgScore) * 0.45
                + (100 - submitRate) * 0.35
                + pendingRate * 0.20
                + missingRate * 0.20;
        return (int) Math.max(0, Math.min(100, Math.round(risk)));
    }

    @SuppressWarnings("unchecked")
    private String buildClassAnalysisPayload(String courseName, String indicatorName, String teacherName, String className,
                                             int studentCount, int homeworkCount, int expectedRecordCount,
                                             int submittedCount, int gradedCount, long pendingCount,
                                             long returnedCount, long resubmittedPendingCount, int unsubmittedCount,
                                             String submissionRate, String gradingRate, Map<String, Object> overview,
                                             List<Map<String, Object>> homeworkSummaries, Map<String, Object> studentSnapshots) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("课程：").append(StrUtil.blankToDefault(courseName, "未命名课程")).append('\n');
        builder.append("一级指标：").append(StrUtil.blankToDefault(indicatorName, "未设置")).append('\n');
        builder.append("授课教师：").append(StrUtil.blankToDefault(teacherName, "未知")).append('\n');
        builder.append("班级：").append(StrUtil.blankToDefault(className, "未命名班级")).append('\n');
        builder.append("班级人数：").append(studentCount).append('\n');
        builder.append("任务数量：").append(homeworkCount).append("\n\n");

        builder.append("【执行概况】\n");
        builder.append("- 应提交记录数：").append(expectedRecordCount).append('\n');
        builder.append("- 已提交：").append(submittedCount).append("（提交率 ").append(submissionRate).append("）\n");
        builder.append("- 已批改：").append(gradedCount).append("（批改率 ").append(gradingRate).append("）\n");
        builder.append("- 待批改：").append(pendingCount).append('\n');
        builder.append("- 已打回：").append(returnedCount).append('\n');
        builder.append("- 重新提交待批改：").append(resubmittedPendingCount).append('\n');
        builder.append("- 未提交：").append(unsubmittedCount).append("\n\n");

        builder.append("【成绩概况】\n");
        builder.append("- 均分：").append(overview.get("avgScore")).append('\n');
        builder.append("- 最高分：").append(overview.get("maxScore")).append('\n');
        builder.append("- 最低分：").append(overview.get("minScore")).append('\n');
        builder.append("- 达标人数：").append(overview.get("passCount")).append('\n');
        builder.append("- 达标率：").append(overview.get("passRate")).append('\n');
        Map<String, Integer> scoreDistribution = (Map<String, Integer>) overview.get("scoreDistribution");
        builder.append("- 分数分布：不合格 ").append(scoreDistribution.getOrDefault("unqualified", 0))
                .append("，合格 ").append(scoreDistribution.getOrDefault("qualified", 0))
                .append("，良好 ").append(scoreDistribution.getOrDefault("good", 0))
                .append("，优秀 ").append(scoreDistribution.getOrDefault("excellent", 0)).append("\n\n");

        builder.append("【重点任务】\n");
        List<Map<String, Object>> keyHomeworks = homeworkSummaries.stream()
                .sorted(Comparator.comparingInt(this::extractHomeworkRiskScore).reversed())
                .limit(6)
                .toList();
        if (keyHomeworks.isEmpty()) {
            builder.append("- 当前课程下暂无可分析任务\n");
        } else {
            int index = 1;
            for (Map<String, Object> item : keyHomeworks) {
                builder.append(index++).append(". ")
                        .append(item.get("homeworkName"))
                        .append("：风险 ").append(item.get("riskScore"))
                        .append("，提交率 ").append(item.get("submissionRate"))
                        .append("，均分 ").append(item.get("avgScore"))
                        .append("，达标率 ").append(item.get("passRate"))
                        .append("，待批改 ").append(item.get("pendingCount"))
                        .append("，缺交 ").append(item.get("unsubmittedCount"));
                if (item.get("highestStudent") != null && item.get("lowestStudent") != null) {
                    builder.append("，最高 ")
                            .append(item.get("highestStudent")).append(' ').append(item.get("highestScore"))
                            .append("，最低 ")
                            .append(item.get("lowestStudent")).append(' ').append(item.get("lowestScore"));
                }
                builder.append('\n');
            }
        }
        builder.append('\n');

        builder.append("【学生样本】\n");
        appendStudentGroup(builder, "高表现学生", (List<Map<String, Object>>) studentSnapshots.get("topStudents"));
        appendStudentGroup(builder, "低表现学生", (List<Map<String, Object>>) studentSnapshots.get("bottomStudents"));
        appendStudentGroup(builder, "缺交较多学生", (List<Map<String, Object>>) studentSnapshots.get("missingSubmissionStudents"));

        return builder.toString();
    }

    private void appendStudentGroup(StringBuilder builder, String title, List<Map<String, Object>> rows) {
        builder.append("- ").append(title).append("：");
        if (rows == null || rows.isEmpty()) {
            builder.append("无\n");
            return;
        }
        String line = rows.stream()
                .limit(3)
                .map(item -> String.format("%s(均分%s，已提交%s，缺交%s)",
                        item.get("studentName"),
                        item.get("avgScore"),
                        item.get("submittedCount"),
                        item.get("missingCount")))
                .collect(Collectors.joining("；"));
        builder.append(line).append('\n');
    }

    private int extractHomeworkRiskScore(Map<String, Object> item) {
        return Integer.parseInt(String.valueOf(item.getOrDefault("riskScore", 0)));
    }

    private double extractAvgScore(Map<String, Object> row) {
        return Double.parseDouble(String.valueOf(row.getOrDefault("avgScore", "0")));
    }

    private int extractMissingCount(Map<String, Object> row) {
        return Integer.parseInt(String.valueOf(row.getOrDefault("missingCount", 0)));
    }

    private String resolveStudentName(Map<Long, String> userMap, Long studentId) {
        return userMap.getOrDefault(studentId, "未知学生");
    }

    private record ClassAnalysisContext(int studentCount, int homeworkCount, String payload) {
    }

}
