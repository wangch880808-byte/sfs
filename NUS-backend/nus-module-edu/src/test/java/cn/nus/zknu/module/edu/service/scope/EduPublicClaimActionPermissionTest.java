package cn.nus.zknu.module.edu.service.scope;

import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EduPublicClaimActionPermissionTest extends BaseMockitoUnitTest {
    @InjectMocks private EduDataScopeService scope;
    @Mock private EduRoleApi eduRoleApi;
    @Mock private SecurityFrameworkService security;
    @Mock private CapacityCourseApi courses;

    @ParameterizedTest
    @CsvSource({"0,2,0,true,true,false,true", "0,2,0,true,false,true,true",
        "0,2,0,false,true,false,false", "0,2,0,true,false,false,false",
        "1,2,0,true,true,false,false", "0,1,0,true,true,false,false", "0,2,1,true,true,false,false"})
    void claimRequiresOnlinePublicCourseBindPermissionAndTeacherOrAdmin(int type, int audit, int status,
            boolean bind, boolean teacher, boolean admin, boolean expected) {
        var course = new CapacityCourseRespDTO();
        course.setIsPublicCourse(type); course.setPublicAuditStatus(audit); course.setStatus(status);
        lenient().when(security.hasPermission("edu:class-course:bind")).thenReturn(bind);
        lenient().when(eduRoleApi.isCurrentUserTeacher()).thenReturn(teacher);
        lenient().when(eduRoleApi.isCurrentUserAdmin()).thenReturn(admin);
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(expected, scope.canClaimPublicCourse(course));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"7,true,false,false,true", "8,true,false,false,false", "8,true,true,false,true",
        "8,true,false,true,true", "7,false,false,false,false", "8,false,true,true,false",
        "NULL,true,true,true,false"}, nullValues = "NULL")
    void releaseRequiresAnExistingClaimBindPermissionAndRowOwnerAdminOrApprover(Long claimant, boolean bind,
            boolean admin, boolean approver, boolean expected) {
        lenient().when(security.hasPermission("edu:class-course:bind")).thenReturn(bind);
        lenient().when(eduRoleApi.isCurrentUserAdmin()).thenReturn(admin);
        lenient().when(courses.isCourseAdmin(7L)).thenReturn(approver);
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertEquals(expected, scope.canReleasePublicClaim(claimant));
        }
    }
}
