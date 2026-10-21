package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduDeptScopeServiceTest extends BaseMockitoUnitTest {

    @Mock private PermissionService permissionService;
    @Mock private AdminUserMapper userMapper;
    @Mock private EduRoleApi eduRoleApi;
    private UserDeptScopeService userScope;
    private EduDeptScopeService service;

    @BeforeEach
    void setUp() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2), new org.springframework.mock.web.MockHttpServletRequest());
        userScope = new UserDeptScopeService();
        ReflectionTestUtils.setField(userScope, "permissionService", permissionService);
        ReflectionTestUtils.setField(userScope, "userMapper", userMapper);
        service = new EduDeptScopeService();
        ReflectionTestUtils.setField(service, "userDeptScopeService", userScope);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        ReflectionTestUtils.setField(service, "eduRoleApi", eduRoleApi);
        lenient().when(eduRoleApi.getAdminRoleCodes()).thenReturn(Set.of("super_admin", "school_admin"));
        lenient().when(userMapper.selectById(1L)).thenReturn(new AdminUserDO().setId(1L).setDeptId(10L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestCachesSystemAndEduPermissionAndAdminRoleChecks() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(
                        new org.springframework.mock.web.MockHttpServletRequest()));
        when(permissionService.hasAnyRoles(eq(1L), any(String[].class))).thenReturn(true);
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission(false, Set.of(10L, 11L)));
        for (int i = 0; i < 10; i++) {
            assertTrue(service.isDeptScopedAdmin());
            assertTrue(service.isRestricted());
            assertTrue(userScope.isDeptInScope(11L));
            assertFalse(userScope.isDeptInScope(20L));
            assertEquals(Set.of(10L, 11L), service.getDeptDataPermission().getDeptIds());
        }
        verify(userMapper).selectById(1L);
        verify(permissionService).getDeptDataPermission(1L);
        verify(permissionService).hasAnyRoles(eq(1L), any(String[].class));
    }

    @Test
    void noRequestRecomputesPermissionsAndRolesEveryTime() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        when(permissionService.hasAnyRoles(eq(1L), any(String[].class))).thenReturn(true);
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission(false, Set.of(10L)));
        for (int i = 0; i < 3; i++) {
            assertTrue(service.isDeptScopedAdmin());
            assertTrue(userScope.isDeptInScope(10L));
        }
        verify(userMapper, times(6)).selectById(1L);
        verify(permissionService, times(6)).getDeptDataPermission(1L);
        verify(permissionService, times(3)).hasAnyRoles(eq(1L), any(String[].class));
    }

    @Test
    void requestCacheSeparatesUsersAndRequests() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(
                        new org.springframework.mock.web.MockHttpServletRequest()));
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission(false, Set.of(10L)));
        when(userMapper.selectById(2L)).thenReturn(new AdminUserDO().setId(2L).setDeptId(20L));
        when(permissionService.getDeptDataPermission(2L)).thenReturn(permission(false, Set.of(20L)));
        assertTrue(userScope.isDeptInScope(10L));
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(2L).setUserType(2),
                new org.springframework.mock.web.MockHttpServletRequest());
        assertFalse(userScope.isDeptInScope(10L));
        assertTrue(userScope.isDeptInScope(20L));
        verify(userMapper).selectById(1L);
        verify(userMapper).selectById(2L);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(
                        new org.springframework.mock.web.MockHttpServletRequest()));
        assertTrue(userScope.isDeptInScope(20L));
        verify(userMapper, times(2)).selectById(2L);
    }

    @Test
    void collegeAdminCanMaintainOwnCollegeAndDescendantsOnly() {
        when(permissionService.hasAnyRoles(eq(1L), any(String[].class))).thenReturn(true);
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission(false, Set.of(10L, 11L)));

        assertTrue(service.isDeptScopedAdmin());
        assertEquals(Set.of(10L, 11L), service.getDeptDataPermission().getDeptIds());
        assertDoesNotThrow(() -> service.validateDeptInScope(10L));
        assertDoesNotThrow(() -> service.validateDeptInScope(11L));
        assertForbidden(() -> service.validateDeptInScope(20L));
        assertForbidden(() -> service.validateDeptInScope(null));
    }

    @Test
    void restrictedTeacherIsNotDeptScopedAdmin() {
        assertFalse(service.isDeptScopedAdmin());
        verify(permissionService, never()).getDeptDataPermission(anyLong());
    }

    @Test
    void accountWithoutCollegeIsExplicitlyRejectedBeforeResolvingDescendants() {
        when(userMapper.selectById(1L)).thenReturn(new AdminUserDO().setId(1L));
        when(permissionService.hasAnyRoles(1L, "college_admin")).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.validateDeptInScope(11L));
        assertEquals(FORBIDDEN.getCode(), ex.getCode());
        assertEquals("账号未设置所属学院，请联系管理员", ex.getMessage());
        verify(permissionService, never()).getDeptDataPermission(anyLong());
    }

    @Test
    void restrictedAccountWithoutDeptIsRejectedEvenWithCustomDeptIds() {
        when(userMapper.selectById(1L)).thenReturn(new AdminUserDO().setId(1L));
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission(false, Set.of(11L)));
        ServiceException ex = assertThrows(ServiceException.class, service::getDeptDataPermission);
        assertEquals("账号未设置所属学院，请联系管理员", ex.getMessage());
    }

    @Test
    void globalAndSchoolAdminsWithAllScopeAreUnchanged() {
        for (String code : Set.of("super_admin", "school_admin")) {
            when(eduRoleApi.getAdminRoleCodes()).thenReturn(Set.of(code));
            when(permissionService.hasAnyRoles(eq(1L), any(String[].class))).thenReturn(true);
            when(permissionService.getDeptDataPermission(1L)).thenReturn(permission(true, Set.of()));
            assertFalse(service.isDeptScopedAdmin());
            assertDoesNotThrow(() -> service.validateDeptInScope(999L));
            assertDoesNotThrow(() -> service.validateDeptInScope(null));
        }
    }

    @Test
    void selfScopeDoesNotAuthorizeAnArbitraryDepartment() {
        DeptDataPermissionRespDTO permission = permission(false, Set.of());
        permission.setSelf(true);
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission);
        assertForbidden(() -> service.validateDeptInScope(10L));
    }

    @Test
    void backgroundJobWithoutLoginKeepsAllScope() {
        SecurityContextHolder.clearContext();
        assertFalse(service.isDeptScopedAdmin());
        assertTrue(service.getDeptDataPermission().getAll());
        assertDoesNotThrow(() -> service.validateDeptInScope(999L));
        verifyNoInteractions(permissionService);
    }

    @Test
    void zeroAffectedRowsAreForbidden() {
        assertForbidden(() -> service.validateAffectedRows(0));
        assertDoesNotThrow(() -> service.validateAffectedRows(1));
    }

    private DeptDataPermissionRespDTO permission(boolean all, Set<Long> deptIds) {
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setAll(all);
        permission.setDeptIds(deptIds);
        return permission;
    }

    private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class, action).getCode());
    }
}
