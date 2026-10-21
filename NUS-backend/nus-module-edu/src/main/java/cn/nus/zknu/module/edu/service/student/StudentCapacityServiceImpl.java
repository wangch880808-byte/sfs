package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCapacityMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生能力分析 Service 实现类
 */
@Service
public class StudentCapacityServiceImpl implements StudentCapacityService {

    @Resource
    private StudentCapacityMapper studentCapacityMapper;
    @Resource
    private EduClassStudentMapper classStudentMapper;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduMetricPolicy metricPolicy;

    @Override
    public AppStudentCapacityAnalysisRespVO getCapacityAnalysis(Long studentId) {
        List<Long> classIds = getStudentClassIds(studentId);
        AppStudentCapacityAnalysisRespVO resp = new AppStudentCapacityAnalysisRespVO();
        if (classIds.isEmpty()) {
            resp.setIndicators(Collections.emptyList());
            fillPendingAnalysis(resp);
            return resp;
        }
        List<Map<String, Object>> rates = studentCapacityMapper.selectFirstIndicatorRates(studentId, classIds);
        List<AppStudentCapacityAnalysisRespVO.IndicatorRate> indicators = new ArrayList<>();
        for (Map<String, Object> rate : rates) {
            AppStudentCapacityAnalysisRespVO.IndicatorRate item = new AppStudentCapacityAnalysisRespVO.IndicatorRate();
            item.setIndicatorFirstId(toLong(rate.get("indicatorFirstId")));
            item.setIndicatorName((String) rate.get("indicatorName"));
            int total = toInt(rate.get("totalTasks"));
            int submitted = toInt(rate.get("submittedTasks"));
            int pendingReview = toInt(rate.get("pendingReviewTasks"));
            int evaluated = toInt(rate.get("evaluatedTasks"));
            int qualified = toInt(rate.get("qualifiedTasks"));
            item.setTotalTasks(total);
            item.setSubmittedTasks(submitted);
            item.setPendingReviewTasks(pendingReview);
            item.setEvaluatedTasks(evaluated);
            item.setQualifiedTasks(qualified);
            item.setQualifiedRate(calculateRate(qualified, evaluated));
            item.setEvaluationStatus(evaluated > 0 ? "EVALUATED" : "PENDING");
            indicators.add(item);
        }
        resp.setIndicators(indicators);
        Map<String, Object> stats = studentCapacityMapper.selectOverallStats(studentId, classIds);
        Map<Long, Double> ratesByIndicator = new LinkedHashMap<>();
        Map<Long, Double> weightsByIndicator = new LinkedHashMap<>();
        for (int i = 0; i < indicators.size(); i++) {
            AppStudentCapacityAnalysisRespVO.IndicatorRate indicator = indicators.get(i);
            if (indicator.getQualifiedRate() == null) continue;
            ratesByIndicator.put(indicator.getIndicatorFirstId(), indicator.getQualifiedRate());
            Object weight = rates.get(i).get("indicatorWeight");
            if (weight instanceof Number number) weightsByIndicator.put(indicator.getIndicatorFirstId(), number.doubleValue());
        }
        Double overallRate = metricPolicy.weightedRate(ratesByIndicator, weightsByIndicator);
        resp.setTotalTasks(toInt(stats.get("totalTasks")));
        resp.setSubmittedTasks(toInt(stats.get("submittedTasks")));
        resp.setPendingReviewTasks(toInt(stats.get("pendingReviewTasks")));
        resp.setEvaluatedTasks(toInt(stats.get("evaluatedTasks")));
        resp.setQualifiedTasks(toInt(stats.get("qualifiedTasks")));
        resp.setUnindicatedEvaluatedCount(toInt(stats.get("unindicatedEvaluatedCount")));
        resp.setOverallRate(overallRate);
        resp.setQualified(overallRate == null ? null : overallRate >= metricPolicy.certificateThreshold());
        resp.setEvaluationStatus(overallRate != null ? "EVALUATED" : "PENDING");
        return resp;
    }

    @Override
    public AppStudentCapacityDetailRespVO getCapacityDetail(Long studentId, Long indicatorFirstId) {
        List<Long> classIds = getStudentClassIds(studentId);
        AppStudentCapacityDetailRespVO resp = new AppStudentCapacityDetailRespVO();
        resp.setIndicatorFirstId(indicatorFirstId);
        resp.setIndicatorFirstName(studentCapacityMapper.selectIndicatorName(indicatorFirstId));

        if (classIds.isEmpty()) {
            resp.setSecondIndicators(Collections.emptyList());
            resp.setHomeworks(Collections.emptyList());
            return resp;
        }

        // 二级指标达标率
        List<Map<String, Object>> secondRates = studentCapacityMapper.selectSecondIndicatorRates(
                studentId, classIds, indicatorFirstId);
        List<AppStudentCapacityDetailRespVO.SecondIndicator> secondIndicators = new ArrayList<>();
        for (Map<String, Object> rate : secondRates) {
            AppStudentCapacityDetailRespVO.SecondIndicator item = new AppStudentCapacityDetailRespVO.SecondIndicator();
            item.setIndicatorSecondId(toLong(rate.get("indicatorSecondId")));
            item.setIndicatorName((String) rate.get("indicatorName"));
            int total = toInt(rate.get("totalTasks"));
            int submitted = toInt(rate.get("submittedTasks"));
            int pendingReview = toInt(rate.get("pendingReviewTasks"));
            int evaluated = toInt(rate.get("evaluatedTasks"));
            int qualified = toInt(rate.get("qualifiedTasks"));
            item.setTotalTasks(total);
            item.setSubmittedTasks(submitted);
            item.setPendingReviewTasks(pendingReview);
            item.setEvaluatedTasks(evaluated);
            item.setQualifiedTasks(qualified);
            item.setQualifiedRate(calculateRate(qualified, evaluated));
            item.setEvaluationStatus(evaluated > 0 ? "EVALUATED" : "PENDING");
            secondIndicators.add(item);
        }
        resp.setSecondIndicators(secondIndicators);

        // 相关任务列表
        List<Map<String, Object>> hwList = studentCapacityMapper.selectHomeworksByIndicatorFirst(
                studentId, classIds, indicatorFirstId);
        List<AppStudentCapacityDetailRespVO.HomeworkItem> homeworks = new ArrayList<>();
        for (Map<String, Object> hw : hwList) {
            AppStudentCapacityDetailRespVO.HomeworkItem item = new AppStudentCapacityDetailRespVO.HomeworkItem();
            item.setHomeworkId(toLong(hw.get("homeworkId")));
            item.setHomeworkName((String) hw.get("homeworkName"));
            item.setIndicatorSecondId(toLong(hw.get("indicatorSecondId")));
            Integer score = hw.get("score") != null ? toInt(hw.get("score")) : null;
            item.setScore(score);
            int passScore = toInt(hw.get("passScore"));
            Integer recordStatus = hw.get("recordStatus") != null ? toInt(hw.get("recordStatus")) : null;
            item.setRecordStatus(recordStatus);
            item.setQualified(score != null && Objects.equals(recordStatus, EduHomeworkRecordStatusEnum.CORRECTED) ? score >= passScore : null);
            homeworks.add(item);
        }
        resp.setHomeworks(homeworks);
        return resp;
    }

    @Override
    public AppStudentReportBasicRespVO getReportBasic(Long studentId) {
        List<Long> classIds = getStudentClassIds(studentId);
        AppStudentReportBasicRespVO resp = new AppStudentReportBasicRespVO();
        if (classIds.isEmpty()) {
            resp.setTotalTasks(0);
            resp.setSubmittedTasks(0);
            resp.setPendingReviewTasks(0);
            resp.setEvaluatedTasks(0);
            resp.setQualifiedTasks(0);
            resp.setQualifiedRate(null);
            resp.setCertificateEligible(null);
            resp.setEvaluationStatus("PENDING");
            return resp;
        }
        Map<String, Object> stats = studentCapacityMapper.selectOverallStats(studentId, classIds);
        int total = toInt(stats.get("totalTasks"));
        int submitted = toInt(stats.get("submittedTasks"));
        int pendingReview = toInt(stats.get("pendingReviewTasks"));
        int evaluated = toInt(stats.get("evaluatedTasks"));
        int qualified = toInt(stats.get("qualifiedTasks"));
        resp.setTotalTasks(total);
        resp.setSubmittedTasks(submitted);
        resp.setPendingReviewTasks(pendingReview);
        resp.setEvaluatedTasks(evaluated);
        resp.setQualifiedTasks(qualified);
        Double rate = calculateRate(qualified, evaluated);
        resp.setQualifiedRate(rate);
        // 证书以一级指标加权后的能力综合分判定，报告中的任务达标率仍按任务份数展示。
        Boolean capacityQualified = getCapacityAnalysis(studentId).getQualified();
        // 只有无指标任务已评定时，没有能力画像作为获证依据。
        resp.setCertificateEligible(capacityQualified == null && evaluated > 0 ? Boolean.FALSE : capacityQualified);
        resp.setEvaluationStatus(evaluated > 0 ? "EVALUATED" : "PENDING");

        return resp;
    }

    @Override
    public AppStudentReportSkillsRespVO getReportSkills(Long studentId) {
        List<Long> classIds = getStudentClassIds(studentId);
        AppStudentReportSkillsRespVO resp = new AppStudentReportSkillsRespVO();
        if (classIds.isEmpty()) {
            resp.setSkills(Collections.emptyList());
            resp.setStrengths(Collections.emptyList());
            resp.setWeaknesses(Collections.emptyList());
            return resp;
        }
        List<Map<String, Object>> rates = studentCapacityMapper.selectFirstIndicatorRates(studentId, classIds);
        List<AppStudentReportSkillsRespVO.SkillItem> skills = new ArrayList<>();
        for (Map<String, Object> rate : rates) {
            AppStudentReportSkillsRespVO.SkillItem item = new AppStudentReportSkillsRespVO.SkillItem();
            item.setIndicatorName((String) rate.get("indicatorName"));
            int total = toInt(rate.get("totalTasks"));
            int submitted = toInt(rate.get("submittedTasks"));
            int pendingReview = toInt(rate.get("pendingReviewTasks"));
            int evaluated = toInt(rate.get("evaluatedTasks"));
            int qualified = toInt(rate.get("qualifiedTasks"));
            item.setTotalTasks(total);
            item.setSubmittedTasks(submitted);
            item.setPendingReviewTasks(pendingReview);
            item.setEvaluatedTasks(evaluated);
            item.setQualifiedTasks(qualified);
            item.setQualifiedRate(calculateRate(qualified, evaluated));
            item.setEvaluationStatus(evaluated > 0 ? "EVALUATED" : "PENDING");
            skills.add(item);
        }
        resp.setSkills(skills);

        // 优势：达标率>=80%，薄弱：达标率<60%
        resp.setStrengths(skills.stream().filter(s -> s.getQualifiedRate() != null && s.getQualifiedRate() >= 80.0)
                .map(AppStudentReportSkillsRespVO.SkillItem::getIndicatorName).collect(Collectors.toList()));
        resp.setWeaknesses(skills.stream().filter(s -> s.getQualifiedRate() != null && s.getQualifiedRate() < 60.0)
                .map(AppStudentReportSkillsRespVO.SkillItem::getIndicatorName).collect(Collectors.toList()));
        return resp;
    }

    @Override
    public AppStudentReportTrendRespVO getReportTrend(Long studentId) {
        List<Long> classIds = getStudentClassIds(studentId);
        List<Map<String, Object>> scoreRows = classIds.isEmpty() ? Collections.emptyList()
                : studentCapacityMapper.selectScoreTrend(studentId, classIds);
        AppStudentReportTrendRespVO resp = new AppStudentReportTrendRespVO();
        List<AppStudentReportTrendRespVO.TrendItem> trends = new ArrayList<>();
        for (Map<String, Object> row : scoreRows) {
            AppStudentReportTrendRespVO.TrendItem item = new AppStudentReportTrendRespVO.TrendItem();
            item.setRecordDate(java.time.LocalDate.parse(row.get("recordDate").toString()));
            item.setTotalScore(new BigDecimal(row.get("totalScore").toString()));
            trends.add(item);
        }
        resp.setTrends(trends);
        return resp;
    }

    private List<Long> getStudentClassIds(Long studentId) {
        List<EduClassStudentDO> classStudents = classStudentMapper.selectListByStudentIds(
                Collections.singletonList(studentId));
        Set<Long> classIds = classStudents.stream().map(EduClassStudentDO::getClassId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assigneeMapper.selectListByStudentId(studentId).forEach(item -> classIds.add(item.getClassId()));
        return new ArrayList<>(classIds);
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        return ((Number) obj).intValue();
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        return ((Number) obj).longValue();
    }

    private Double calculateRate(int qualified, int evaluated) {
        return evaluated > 0 ? BigDecimal.valueOf(qualified * 100.0 / evaluated)
                .setScale(1, RoundingMode.HALF_UP).doubleValue() : null;
    }

    private void fillPendingAnalysis(AppStudentCapacityAnalysisRespVO resp) {
        resp.setOverallRate(null);
        resp.setQualified(null);
        resp.setEvaluationStatus("PENDING");
        resp.setTotalTasks(0);
        resp.setSubmittedTasks(0);
        resp.setPendingReviewTasks(0);
        resp.setEvaluatedTasks(0);
        resp.setQualifiedTasks(0);
        resp.setUnindicatedEvaluatedCount(0);
    }

}
