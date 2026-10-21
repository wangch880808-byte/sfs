package cn.nus.zknu.server.security;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 功能权限校验不得因“已登录”而整体放行
 */
class SecurityFrameworkServiceImplTest {

    private PermissionCommonApi permissionApi;
    private SecurityFrameworkServiceImpl securityService;

    @BeforeEach
    void setUp() {
        permissionApi = mock(PermissionCommonApi.class);
        securityService = new SecurityFrameworkServiceImpl(permissionApi);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousUser_isDenied() {
        assertFalse(securityService.hasPermission("system:user:query"));
        assertFalse(securityService.hasAnyRoles("student"));
        assertFalse(securityService.hasScope("user.read"));
        verify(permissionApi, never()).hasAnyPermissions(anyLong(), any(String[].class));
    }

    @Test
    void loggedInUserWithoutPermission_isDenied() {
        login(5L, Collections.emptyList());
        when(permissionApi.hasAnyPermissions(5L, "system:user:query")).thenReturn(false);
        when(permissionApi.hasAnyRoles(5L, "super_admin")).thenReturn(false);

        assertFalse(securityService.hasPermission("system:user:query"));
        assertFalse(securityService.hasAnyRoles("super_admin"));
    }

    @Test
    void loggedInUser_delegatesToGrantedPermissions() {
        login(5L, Collections.emptyList());
        when(permissionApi.hasAnyPermissions(5L, "edu:homework:query", "edu:class:query")).thenReturn(true);
        when(permissionApi.hasAnyRoles(5L, "teacher")).thenReturn(true);

        assertTrue(securityService.hasAnyPermissions("edu:homework:query", "edu:class:query"));
        assertTrue(securityService.hasRole("teacher"));
    }

    @Test
    void miniProgramToken_onlyKeepsStudentHomeworkPermissions() {
        login(9L, List.of("miniapp:student"));
        when(permissionApi.getMiniProgramRoleCodes()).thenReturn(Set.of("student"));
        when(permissionApi.hasAnyPermissions(9L, "edu:student-homework:query")).thenReturn(true);
        when(permissionApi.hasAnyRoles(9L, "student")).thenReturn(true);

        assertFalse(securityService.hasPermission("system:user:query"));
        assertFalse(securityService.hasAnyRoles("super_admin", "school_admin"));
        assertTrue(securityService.hasPermission("edu:student-homework:query"));
        assertTrue(securityService.hasRole("student"));
        verify(permissionApi, never()).hasAnyPermissions(9L, "system:user:query");
    }

    @Test
    void miniProgramToken_followsConfiguredStudentRoleCode() {
        login(9L, List.of("miniapp:student"));
        when(permissionApi.getMiniProgramRoleCodes()).thenReturn(Set.of("xs"));
        when(permissionApi.hasAnyRoles(9L, "xs")).thenReturn(true);

        assertFalse(securityService.hasRole("student"));
        assertTrue(securityService.hasAnyRoles("student", "xs"));
        verify(permissionApi, never()).hasAnyRoles(9L, "student");
    }

    @Test
    void scopes_areCheckedAgainstToken() {
        login(5L, List.of("user.read"));

        assertTrue(securityService.hasScope("user.read"));
        assertFalse(securityService.hasScope("user.write"));
    }

    @Test
    void skipPermissionCheck_isRemoved() {
        assertTrue(Arrays.stream(SecurityFrameworkUtils.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("skipPermissionCheck")));
    }

    private void login(Long userId, List<String> scopes) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setScopes(scopes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

}
