package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class EduStatisticsDashboardTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduStatisticsServiceImpl service;
    @Mock
    private StatisticsMapper statisticsMapper;
    @Mock
    private CapacityCourseApi capacityCourseApi;
    @Mock
    private cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService dataScopeService;
    @Mock
    private cn.nus.zknu.framework.security.core.service.SecurityFrameworkService securityFrameworkService;

    @Test
    void approverReceivesPendingPublicCourseCount() {
        when(dataScopeService.isCourseApprover()).thenReturn(true);
        when(statisticsMapper.selectPendingPublicAuditCount()).thenReturn(2);
        when(statisticsMapper.selectTotalCourses()).thenReturn(10L);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(50L);
            var dashboard = service.getAdminDashboard();
            assertEquals(2, dashboard.getPendingPublicAuditCount());
            assertEquals(10L, dashboard.getTotalCourses());
        }
        verify(dataScopeService).isCourseApprover();
        verify(statisticsMapper).selectPendingPublicAuditCount();
    }

    @Test
    void globalAdminWithoutAuditPermissionReceivesNullAndDoesNotQueryCount() {
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertNull(service.getAdminDashboard().getPendingPublicAuditCount());
        }
        verify(dataScopeService).isCourseApprover();
        verify(statisticsMapper, never()).selectPendingPublicAuditCount();
    }

    @Test
    void approverWithNoPendingCoursesReceivesZero() {
        when(dataScopeService.isCourseApprover()).thenReturn(true);
        when(statisticsMapper.selectPendingPublicAuditCount()).thenReturn(0);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(50L);
            assertEquals(0, service.getAdminDashboard().getPendingPublicAuditCount());
        }
    }
    @Test
    void ordinaryTeacherIsRejectedBeforeAnyStatisticsQuery() {
        var error = org.junit.jupiter.api.Assertions.assertThrows(
                cn.nus.zknu.framework.common.exception.ServiceException.class, service::getAdminDashboard);
        assertEquals(cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(statisticsMapper, capacityCourseApi);
    }

    @Test
    void collegeAdminRetainsExistingDashboardQueries() {
        when(securityFrameworkService.hasRole("college_admin")).thenReturn(true);
        when(statisticsMapper.selectTotalStudents()).thenReturn(12L);
        assertEquals(12L, service.getAdminDashboard().getTotalStudents());
        verify(statisticsMapper).selectTotalTeachers();
        verify(statisticsMapper).selectTotalCourses();
        verify(statisticsMapper).selectTotalHomeworks();
        verify(statisticsMapper).selectWeekSubmissions(null);
        verify(statisticsMapper).selectPendingCorrections(null);
        verify(statisticsMapper).selectAiCorrections();
        verify(statisticsMapper, never()).selectPendingPublicAuditCount();
    }

}
