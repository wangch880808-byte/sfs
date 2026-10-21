package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.statistics.vo.TotalPassRateReqVO;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 统计入口独立校验课程及班级，授权失败时不得读取聚合数据。 */
class EduStatisticsScopeTest extends BaseMockitoUnitTest {
    @InjectMocks private EduStatisticsServiceImpl service;
    @Mock private StatisticsMapper mapper;
    @Mock private EduDataScopeService scope;
    @Mock private EduMetricPolicy policy;

    @ParameterizedTest
    @CsvSource({
        "true,CLAIMANT,true", "true,UNCLAIMED,false", "true,TEAM,false", "true,TEAM_CLAIMANT,true",
        "true,OWNER,true", "true,ADMIN,true", "true,APPROVER,true",
        "false,CLAIMANT,false", "false,UNCLAIMED,false", "false,TEAM,true", "false,TEAM_CLAIMANT,true",
        "false,OWNER,true", "false,ADMIN,true", "false,APPROVER,true"
    })
    void courseAndOwnClassRoleMatrix(boolean publicCourse, String role, boolean allowed) {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            boolean all = Set.of("OWNER", "ADMIN", "APPROVER").contains(role);
            boolean claim = role.equals("CLAIMANT") || role.equals("TEAM_CLAIMANT");
            lenient().when(scope.isPublicCourse(20L)).thenReturn(publicCourse);
            lenient().when(scope.isGlobalAdmin()).thenReturn(role.equals("ADMIN"));
            lenient().when(scope.isCourseApprover()).thenReturn(role.equals("APPROVER"));
            lenient().when(scope.canViewAllPublic(20L)).thenReturn(all);
            lenient().when(scope.claimedClassIds(20L)).thenReturn(claim ? Set.of(1L) : Set.of());
            lenient().when(scope.canViewPublicClass(20L, 1L)).thenReturn(all || claim);
            lenient().when(scope.canManageCourse(20L)).thenReturn(all || role.startsWith("TEAM"));
            lenient().when(policy.scoreRatios()).thenReturn(new EduMetricPolicy.ScoreRatios(0.8, 0.9));
            for (Long classId : new Long[]{null, 1L}) {
                var req = new TotalPassRateReqVO();
                req.setCourseId(20L);
                req.setClassId(classId);
                if (!allowed) {
                    forbidden(() -> service.getWorkTrend(20L, classId));
                    forbidden(() -> service.getCourseAnalysis(20L, classId));
                    forbidden(() -> service.getTotalPassRate(req));
                    continue;
                }
                Long claimTeacher = publicCourse && !all ? 7L : null;
                when(mapper.selectWorkTrend(20L, classId, claimTeacher))
                        .thenReturn(List.of(Map.of("workName", "任务", "avgScore", 72.5, "maxScore", 90, "minScore", 50)));
                when(mapper.selectCourseAnalysisAvg(20L, classId, claimTeacher)).thenReturn(List.of());
                assertEquals(List.of("任务"), service.getWorkTrend(20L, classId).getWorkNames());
                assertEquals(List.of(72.5), service.getWorkTrend(20L, classId).getAvgScores());
                assertTrue(service.getCourseAnalysis(20L, classId).getHomeworks().isEmpty());
                assertEquals(0, service.getTotalPassRate(req).getTotalCount());
                Long passTeacher = role.equals("ADMIN") || role.equals("APPROVER") || publicCourse && all ? null : 7L;
                verify(mapper).selectTotalPassRate(passTeacher, classId == null ? null : List.of(classId), 20L, null, 0.8, 0.9);
            }
            if (allowed) {
                service.getClassOptions(20L);
                verify(mapper).selectCourseClasses(role.equals("ADMIN") || role.equals("APPROVER") ? null : 7L, 20L);
            } else {
                forbidden(() -> service.getClassOptions(20L));
                if (!publicCourse) verify(mapper, atLeastOnce()).selectStatisticsCourseAccess(7L, 20L);
                else verifyNoInteractions(mapper);
            }
        }
    }

    @Test
    void claimantCannotRequestAnotherPublicClassEvenWithAnOwnedTaskThere() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            when(scope.isPublicCourse(20L)).thenReturn(true);
            var req = new TotalPassRateReqVO();
            req.setCourseId(20L);
            req.setClassId(2L);
            forbidden(() -> service.getWorkTrend(20L, 2L));
            forbidden(() -> service.getCourseAnalysis(20L, 2L));
            forbidden(() -> service.getTotalPassRate(req));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void exclusiveCourseStillUsesExistingClassCourseAccessRule() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            when(scope.canManageCourse(20L)).thenReturn(true);
            doThrow(new ServiceException(FORBIDDEN)).when(scope).validateClassCourseAccess(2L, 20L);
            forbidden(() -> service.getWorkTrend(20L, 2L));
            forbidden(() -> service.getCourseAnalysis(20L, 2L));
            var req = new TotalPassRateReqVO();
            req.setCourseId(20L);
            req.setClassId(2L);
            forbidden(() -> service.getTotalPassRate(req));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void exclusiveAssistantMayRequestCourseStatisticsWithOrWithoutClass() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            when(mapper.selectStatisticsCourseAccess(7L, 20L)).thenReturn(true);
            when(policy.scoreRatios()).thenReturn(new EduMetricPolicy.ScoreRatios(0.8, 0.9));
            for (Long classId : new Long[]{null, 1L}) {
                service.getWorkTrend(20L, classId);
                service.getCourseAnalysis(20L, classId);
                var request = new TotalPassRateReqVO(); request.setCourseId(20L); request.setClassId(classId);
                service.getTotalPassRate(request);
                verify(mapper).selectWorkTrend(20L, classId, null);
                verify(mapper).selectCourseAnalysisAvg(20L, classId, null);
            }
            verify(scope, times(3)).validateClassCourseAccess(1L, 20L);
        }
    }

    @Test
    void classOnlyRequestValidatesScopeBeforeDepartmentOrScoreQueries() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var req = new TotalPassRateReqVO();
            req.setClassId(2L);
            req.setCollegeId(100L);
            forbidden(() -> service.getTotalPassRate(req));
            verify(mapper).selectStatisticsClassAccess(7L, 2L);
            verifyNoMoreInteractions(mapper);
            when(mapper.selectStatisticsClassAccess(7L, 2L)).thenReturn(true);
            when(policy.scoreRatios()).thenReturn(new EduMetricPolicy.ScoreRatios(0.8, 0.9));
            assertEquals(0, service.getTotalPassRate(req).getTotalCount());
            verify(mapper).selectTotalPassRate(7L, List.of(2L), null, null, 0.8, 0.9);
        }
    }

    @Test
    void courseOptionsUseGlobalScopeOnlyForAdministratorsAndApprovers() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.getCourseOptions();
            verify(mapper).selectTeacherCourses(7L);
            when(scope.isCourseApprover()).thenReturn(true);
            service.getCourseOptions();
            verify(mapper).selectTeacherCourses(null);
        }
    }

    @Test
    void teacherDashboardKeepsAssignmentOwnershipAndNeverAcceptsAnotherTeacherId() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            when(mapper.selectTeacherCourseCount(7L)).thenReturn(3L);
            when(mapper.selectUnreadMessages(7L)).thenReturn(2L);
            var resp = service.getTeacherDashboard();
            assertEquals(3L, resp.getMyCourses());
            assertEquals(2L, resp.getUnreadMessages());
            verify(mapper).selectTeacherCourseCount(7L);
            verify(mapper).selectUnreadMessages(7L);
            verify(mapper).selectPendingCorrections(7L);
            verify(mapper).selectWeekSubmissions(7L);
            verify(mapper).selectRecentHomeworks(7L);
            verifyNoMoreInteractions(mapper);
        }
    }

    @Test
    void anonymousRequestsCannotBecomeGlobalQueries() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            forbidden(() -> service.getWorkTrend(20L, null));
            forbidden(() -> service.getCourseAnalysis(20L, null));
            forbidden(() -> service.getTotalPassRate(new TotalPassRateReqVO()));
            forbidden(service::getCourseOptions);
            forbidden(() -> service.getClassOptions(20L));
            forbidden(service::getTeacherDashboard);
            verifyNoInteractions(mapper);
        }
    }

    private void forbidden(Runnable action) {
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class, action::run).getCode());
    }
}
