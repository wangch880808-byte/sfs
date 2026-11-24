package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.edu.controller.admin.statistics.vo.*;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
public class EduStatisticsServiceImpl implements EduStatisticsService {

    @Resource
    private StatisticsMapper statisticsMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduMetricPolicy metricPolicy;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public TotalPassRateRespVO getTotalPassRate(TotalPassRateReqVO reqVO) {
        Long teacherId = resolveTeacherId();
        if (reqVO.getCourseId() != null) {
            validateStatisticsScope(reqVO.getCourseId(), reqVO.getClassId());
            if (dataScopeService.isPublicCourse(reqVO.getCourseId())
                    && dataScopeService.canViewAllPublic(reqVO.getCourseId())) {
                teacherId = null;
            }
        } else if (reqVO.getClassId() != null && teacherId != null
                && !statisticsMapper.selectStatisticsClassAccess(teacherId, reqVO.getClassId())) {
            throw exception(FORBIDDEN);
        }
        // 如果传了学院/专业，查对应班级ID列表
        List<Long> classIds = null;
        if (reqVO.getClassId() != null) {
            classIds = Collections.singletonList(reqVO.getClassId());
        } else if (reqVO.getCollegeId() != null || reqVO.getMajorId() != null) {
            classIds = statisticsMapper.selectClassIdsByDept(reqVO.getCollegeId(), reqVO.getMajorId());
            if (classIds == null || classIds.isEmpty()) {
                return buildEmptyPassRate();
            }
        }

        EduMetricPolicy.ScoreRatios ratios = metricPolicy.scoreRatios();
        Map<String, Object> result = statisticsMapper.selectTotalPassRate(
                teacherId, classIds, reqVO.getCourseId(), reqVO.getAcademicYear(), ratios.good(), ratios.excellent());
        return buildPassRateResp(result);
    }

    @Override
    public WorkTrendRespVO getWorkTrend(Long courseId, Long classId) {
        Long claimTeacherId = validateStatisticsScope(courseId, classId);
        List<Map<String, Object>> rows = statisticsMapper.selectWorkTrend(courseId, classId, claimTeacherId);
        WorkTrendRespVO resp = new WorkTrendRespVO();
        List<String> workNames = new ArrayList<>();
        List<Double> avgScores = new ArrayList<>();
        List<Integer> maxScores = new ArrayList<>();
        List<Integer> minScores = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            workNames.add((String) row.get("workName"));
            avgScores.add(toDouble(row.get("avgScore")));
            maxScores.add(toInt(row.get("maxScore")));
            minScores.add(toInt(row.get("minScore")));
        }
        resp.setWorkNames(workNames);
        resp.setAvgScores(avgScores);
        resp.setMaxScores(maxScores);
        resp.setMinScores(minScores);
        return resp;
    }

    @Override
    public CourseAnalysisRespVO getCourseAnalysis(Long courseId, Long classId) {
        Long claimTeacherId = validateStatisticsScope(courseId, classId);
        List<Map<String, Object>> avgRows = statisticsMapper.selectCourseAnalysisAvg(courseId, classId, claimTeacherId);
        Map<Long, List<Map<String, Object>>> extremesByHomework = new HashMap<>();
        if (!avgRows.isEmpty()) {
            List<Long> homeworkIds = avgRows.stream().map(row -> toLong(row.get("homeworkId"))).toList();
            for (Map<String, Object> row : statisticsMapper.selectHomeworkStudentExtremes(homeworkIds)) {
                extremesByHomework.computeIfAbsent(toLong(row.get("homeworkId")), ignored -> new ArrayList<>()).add(row);
            }
        }
        List<CourseAnalysisRespVO.HomeworkAnalysis> homeworks = new ArrayList<>();
        for (Map<String, Object> row : avgRows) {
            CourseAnalysisRespVO.HomeworkAnalysis ha = new CourseAnalysisRespVO.HomeworkAnalysis();
            Long hwId = toLong(row.get("homeworkId"));
            ha.setHomeworkId(hwId);
            ha.setHomeworkName((String) row.get("homeworkName"));
            ha.setAvgScore(toDouble(row.get("avgScore")));
            List<Map<String, Object>> extremes = extremesByHomework.getOrDefault(hwId, Collections.emptyList());
            ha.setTop5(buildRankedStudentScores(extremes, "topRank"));
            ha.setLast5(buildRankedStudentScores(extremes, "lastRank"));
            homeworks.add(ha);
        }
        CourseAnalysisRespVO resp = new CourseAnalysisRespVO();
        resp.setHomeworks(homeworks);
        return resp;
    }

    @Override
    public List<Map<String, Object>> getCourseOptions() {
        Long teacherId = resolveTeacherId();
        return statisticsMapper.selectTeacherCourses(teacherId);
    }

    @Override
    public List<Map<String, Object>> getClassOptions(Long courseId) {
        validateStatisticsScope(courseId, null);
        Long teacherId = resolveTeacherId();
        return statisticsMapper.selectCourseClasses(teacherId, courseId);
    }

    @Override
    public AdminDashboardRespVO getAdminDashboard() {
        boolean courseApprover = dataScopeService.isCourseApprover();
        // 学院管理员的工作台也使用此接口，保留原有查询与院系拦截，不提升为全局身份。
        if (!dataScopeService.isGlobalAdmin() && !courseApprover
                && !securityFrameworkService.hasRole("college_admin")) {
            throw exception(FORBIDDEN);
        }
        AdminDashboardRespVO resp = new AdminDashboardRespVO();
        resp.setTotalStudents(statisticsMapper.selectTotalStudents());
        resp.setTotalTeachers(statisticsMapper.selectTotalTeachers());
        resp.setTotalCourses(statisticsMapper.selectTotalCourses());
        resp.setTotalHomeworks(statisticsMapper.selectTotalHomeworks());
        resp.setWeekSubmissions(statisticsMapper.selectWeekSubmissions(null));
        resp.setPendingCorrections(statisticsMapper.selectPendingCorrections(null));
        resp.setAiCorrections(statisticsMapper.selectAiCorrections());
        resp.setTermCount(statisticsMapper.selectTermCount());
        resp.setOfficialCourseCount(statisticsMapper.selectOfficialCourseCount());
        resp.setClassCourseCount(statisticsMapper.selectClassCourseCount());
        resp.setActiveClassCount(statisticsMapper.selectActiveClassCount());
        List<Map<String, Object>> termRows = statisticsMapper.selectRecentTerms();
        List<AdminDashboardRespVO.TermSummary> terms = new ArrayList<>();
        for (Map<String, Object> row : termRows) {
            AdminDashboardRespVO.TermSummary t = new AdminDashboardRespVO.TermSummary();
            t.setName((String) row.get("name"));
            t.setStartsOn(String.valueOf(row.get("startsOn")));
            t.setEndsOn(String.valueOf(row.get("endsOn")));
            t.setStatus(row.get("status") != null ? ((Number) row.get("status")).intValue() : 0);
            terms.add(t);
        }
        resp.setRecentTerms(terms);
        if (courseApprover) {
            resp.setPendingPublicAuditCount(statisticsMapper.selectPendingPublicAuditCount());
        }
        return resp;
    }

    @Override
    public TeacherDashboardRespVO getTeacherDashboard() {
        Long teacherId = requireLoginUser();
        TeacherDashboardRespVO resp = new TeacherDashboardRespVO();
        resp.setMyCourses(statisticsMapper.selectTeacherCourseCount(teacherId));
        resp.setPendingCorrections(statisticsMapper.selectPendingCorrections(teacherId));
        resp.setUnreadMessages(statisticsMapper.selectUnreadMessages(teacherId));
        resp.setWeekSubmissions(statisticsMapper.selectWeekSubmissions(teacherId));
        resp.setRecentHomeworks(statisticsMapper.selectRecentHomeworks(teacherId));
        return resp;
    }

    // ========== 工具方法 ==========

    private TotalPassRateRespVO buildPassRateResp(Map<String, Object> result) {
        TotalPassRateRespVO resp = new TotalPassRateRespVO();
        if (result == null || result.get("totalRecords") == null) {
            return buildEmptyPassRate();
        }
        int total = toInt(result.get("totalRecords"));
        resp.setTotalCount(total);
        if (total == 0) {
            resp.setUnqualifiedRate(0.0);
            resp.setQualifiedRate(0.0);
            resp.setGoodRate(0.0);
            resp.setExcellentRate(0.0);
        } else {
            resp.setUnqualifiedRate(Math.round(toInt(result.get("unqualifiedCount")) * 10000.0 / total) / 100.0);
            resp.setQualifiedRate(Math.round(toInt(result.get("qualifiedCount")) * 10000.0 / total) / 100.0);
            resp.setGoodRate(Math.round(toInt(result.get("goodCount")) * 10000.0 / total) / 100.0);
            resp.setExcellentRate(Math.round(toInt(result.get("excellentCount")) * 10000.0 / total) / 100.0);
        }
        return resp;
    }

    private TotalPassRateRespVO buildEmptyPassRate() {
        TotalPassRateRespVO resp = new TotalPassRateRespVO();
        resp.setTotalCount(0);
        resp.setUnqualifiedRate(0.0);
        resp.setQualifiedRate(0.0);
        resp.setGoodRate(0.0);
        resp.setExcellentRate(0.0);
        return resp;
    }

    private List<CourseAnalysisRespVO.StudentScore> buildRankedStudentScores(List<Map<String, Object>> rows, String rankKey) {
        // 窗口结果本身无序，响应必须分别按两种分数方向的编号排列。
        return buildStudentScores(rows.stream().filter(row -> toInt(row.get(rankKey)) <= 5)
                .sorted(Comparator.comparingInt(row -> toInt(row.get(rankKey)))).toList());
    }

    private List<CourseAnalysisRespVO.StudentScore> buildStudentScores(List<Map<String, Object>> rows) {
        List<CourseAnalysisRespVO.StudentScore> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            CourseAnalysisRespVO.StudentScore ss = new CourseAnalysisRespVO.StudentScore();
            ss.setStudentId(toLong(row.get("studentId")));
            ss.setStudentName((String) row.get("studentName"));
            ss.setScore(toInt(row.get("score")));
            list.add(ss);
        }
        return list;
    }

    private Long resolveTeacherId() {
        Long userId = requireLoginUser();
        return dataScopeService.isGlobalAdmin() || dataScopeService.isCourseApprover() ? null : userId;
    }

    /** 统计不能复用仍允许在线公共课浏览的课程入口校验。 */
    private Long validateStatisticsScope(Long courseId, Long classId) {
        Long userId = requireLoginUser();
        if (courseId == null) {
            throw exception(FORBIDDEN);
        }
        if (dataScopeService.isPublicCourse(courseId)) {
            boolean viewAll = dataScopeService.canViewAllPublic(courseId);
            if (classId != null) {
                if (!dataScopeService.canViewPublicClass(courseId, classId)) {
                    throw exception(FORBIDDEN);
                }
            } else if (!viewAll && dataScopeService.claimedClassIds(courseId).isEmpty()) {
                throw exception(FORBIDDEN);
            }
            return viewAll ? null : userId;
        }
        if (!dataScopeService.isGlobalAdmin() && !dataScopeService.isCourseApprover()) {
            if (!dataScopeService.canManageCourse(courseId)
                    && !statisticsMapper.selectStatisticsCourseAccess(userId, courseId)) {
                throw exception(FORBIDDEN);
            }
            if (classId != null) {
                dataScopeService.validateClassCourseAccess(classId, courseId);
            }
        }
        return null;
    }

    private Long requireLoginUser() {
        Long userId = getLoginUserId();
        if (userId == null) {
            throw exception(FORBIDDEN);
        }
        return userId;
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(obj.toString());
    }

    private double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.parseDouble(obj.toString());
    }

}
