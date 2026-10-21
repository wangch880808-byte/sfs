package cn.nus.zknu.module.system.api.permission;

import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EduRoleApiImplTest extends BaseMockitoUnitTest {

    @Mock
    private ConfigApi configApi;
    @Mock
    private PermissionService permissionService;
    @Mock
    private SecurityFrameworkService securityFrameworkService;

    private EduRoleApiImpl api;

    @BeforeEach
    void setUp() {
        api = new EduRoleApiImpl();
        ReflectionTestUtils.setField(api, "configApi", configApi);
        ReflectionTestUtils.setField(api, "permissionService", permissionService);
        ReflectionTestUtils.setField(api, "securityFrameworkService", securityFrameworkService);
    }

    @Test
    void missingConfig_usesDefaultsWithoutCollegeAdmin() {
        EduRoleApiImpl.RoleCodes codes = api.load();

        assertEquals(List.of("super_admin", "admin", "school_admin"), List.copyOf(codes.adminCodes()));
        assertEquals(Set.of("super_admin"), codes.superAdminCodes());
        assertEquals("teacher", codes.teacherCode());
        assertEquals("student", codes.studentCode());
        assertFalse(codes.adminCodes().contains("ADMIN"));
    }

    @Test
    void configuredLists_areTrimmedDedupedAndAcceptChineseSeparators() {
        config(Map.of(EduRoleApi.ADMIN_CODES_KEY, " school_admin，dept_admin ; school_admin ,, ",
                EduRoleApi.SUPER_ADMIN_CODES_KEY, "super_admin, root",
                EduRoleApi.TEACHER_CODE_KEY, " js ",
                EduRoleApi.STUDENT_CODE_KEY, "xs"));

        EduRoleApiImpl.RoleCodes codes = api.load();

        // 超级管理员角色编码总是并入管理员集合
        assertEquals(List.of("school_admin", "dept_admin", "super_admin", "root"), List.copyOf(codes.adminCodes()));
        assertEquals(List.of("super_admin", "root"), List.copyOf(codes.superAdminCodes()));
        assertEquals("js", codes.teacherCode());
        assertEquals("xs", codes.studentCode());
    }

    @Test
    void invalidValues_fallBackToDefaults() {
        config(Map.of(EduRoleApi.ADMIN_CODES_KEY, "管理员,'x'",
                EduRoleApi.SUPER_ADMIN_CODES_KEY, "   ",
                EduRoleApi.TEACHER_CODE_KEY, "teacher role",
                EduRoleApi.STUDENT_CODE_KEY, "school_admin"));

        EduRoleApiImpl.RoleCodes codes = api.load();

        assertEquals(Set.of("super_admin", "admin", "school_admin"), codes.adminCodes());
        assertEquals(Set.of("super_admin"), codes.superAdminCodes());
        assertEquals("teacher", codes.teacherCode());
        // 学生角色不能与管理员角色重合，否则小程序令牌会认到管理员角色
        assertEquals("student", codes.studentCode());
    }

    @Test
    void invalidTokensAreDroppedButValidOnesKept() {
        config(Map.of(EduRoleApi.ADMIN_CODES_KEY, "school_admin, 教务处"));

        assertEquals(List.of("school_admin", "super_admin"), List.copyOf(api.load().adminCodes()));
    }

    @Test
    void sameTeacherAndStudentCode_fallsBackToDefaults() {
        config(Map.of(EduRoleApi.TEACHER_CODE_KEY, "member", EduRoleApi.STUDENT_CODE_KEY, "member"));

        EduRoleApiImpl.RoleCodes codes = api.load();

        assertEquals("teacher", codes.teacherCode());
        assertEquals("student", codes.studentCode());
    }

    @Test
    void configReadFailure_usesDefaults() {
        when(configApi.getConfigValueByKey(anyString())).thenThrow(new IllegalStateException("db down"));

        EduRoleApiImpl.RoleCodes codes = api.load();

        assertEquals(Set.of("super_admin", "admin", "school_admin"), codes.adminCodes());
        assertEquals("student", codes.studentCode());
    }

    @Test
    void valuesAreCachedBetweenCalls() {
        config(Map.of(EduRoleApi.TEACHER_CODE_KEY, "js"));

        assertEquals("js", api.getTeacherRoleCode());
        assertEquals("js", api.getTeacherRoleCode());
        assertEquals("student", api.getStudentRoleCode());

        verify(configApi, times(1)).getConfigValueByKey(EduRoleApi.TEACHER_CODE_KEY);
    }

    @Test
    void currentUserChecks_goThroughSecurityServiceWithConfiguredCodes() {
        config(Map.of(EduRoleApi.ADMIN_CODES_KEY, "school_admin"));
        when(securityFrameworkService.hasAnyRoles("school_admin", "super_admin")).thenReturn(true);
        when(securityFrameworkService.hasAnyRoles("super_admin")).thenReturn(false);
        when(securityFrameworkService.hasAnyRoles("teacher")).thenReturn(true);

        assertTrue(api.isCurrentUserAdmin());
        assertFalse(api.isCurrentUserSuperAdmin());
        assertTrue(api.isCurrentUserTeacher());
    }

    @Test
    void userChecks_useEnabledRolesOfThatUser() {
        when(permissionService.hasAnyRoles(5L, "super_admin")).thenReturn(true);
        when(permissionService.hasAnyRoles(6L, "teacher")).thenReturn(true);
        when(permissionService.hasAnyRoles(7L, "student")).thenReturn(false);

        assertTrue(api.isSuperAdmin(5L));
        assertTrue(api.isTeacher(6L));
        assertFalse(api.isStudent(7L));
        assertFalse(api.isTeacher(null));
        verify(permissionService, never()).hasAnyRoles(isNull(), any(String[].class));
    }

    private void config(Map<String, String> values) {
        lenient().when(configApi.getConfigValueByKey(anyString()))
                .thenAnswer(invocation -> values.get(invocation.<String>getArgument(0)));
    }

}
