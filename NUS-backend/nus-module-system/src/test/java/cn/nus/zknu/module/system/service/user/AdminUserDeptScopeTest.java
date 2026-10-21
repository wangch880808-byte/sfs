package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.dal.mysql.user.UserClassScopeMapper;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserDeptScopeTest extends BaseMockitoUnitTest {

    @Mock private AdminUserMapper userMapper;
    @Mock private PermissionService permissionService;
    @Mock private UserClassScopeMapper classScopeMapper;
    private UserDeptScopeService scope;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2), new org.springframework.mock.web.MockHttpServletRequest());
        scope = new UserDeptScopeService();
        ReflectionTestUtils.setField(scope, "userMapper", userMapper);
        ReflectionTestUtils.setField(scope, "permissionService", permissionService);
        ReflectionTestUtils.setField(scope, "classScopeMapper", classScopeMapper);
        service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "deptScopeService", scope);
        lenient().when(userMapper.selectById(1L)).thenReturn(new AdminUserDO().setId(1L).setDeptId(10L));
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setDeptIds(Set.of(10L, 11L));
        lenient().when(permissionService.getDeptDataPermission(1L)).thenReturn(permission);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsForeignDepartmentBeforeInsertingUser() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUserType(1);
        req.setDeptId(20L);
        assertForbidden(() -> service.createUser(req));
        verify(userMapper, never()).insert(any(AdminUserDO.class));
    }

    @Test
    void foreignAccountCannotBeModifiedDisabledRestoredOrReset() {
        when(userMapper.selectById(2L)).thenReturn(new AdminUserDO().setId(2L).setDeptId(20L));
        UserSaveReqVO req = new UserSaveReqVO();
        req.setId(2L);
        req.setDeptId(10L);
        assertForbidden(() -> service.updateUser(req));
        assertForbidden(() -> service.updateUserStatus(2L, 1));
        assertForbidden(() -> service.updateUserStatus(2L, 0));
        assertForbidden(() -> service.updateUserPassword(2L, "resetPass1"));
        verify(userMapper, never()).updateById(any(AdminUserDO.class));
    }

    @Test
    void movingOwnAccountToForeignDepartmentIsForbidden() {
        when(userMapper.selectById(2L)).thenReturn(new AdminUserDO().setId(2L).setDeptId(11L));
        UserSaveReqVO req = new UserSaveReqVO();
        req.setId(2L);
        req.setDeptId(20L);
        assertForbidden(() -> service.updateUser(req));
        verify(userMapper, never()).updateById(any(AdminUserDO.class));
    }

    @Test
    void hiddenOrMissingAccountReturnsForbidden() {
        assertForbidden(() -> service.updateUserStatus(2L, 1));
    }

    @Test
    void systemUserFormCannotAttachStudentToForeignClass() {
        when(classScopeMapper.selectDeptId(99L)).thenReturn(20L);
        assertForbidden(() -> scope.validateClassInScope(99L));
    }

    @Test
    void schoolAdminAllScopeStillAllowsCrossCollegeTargets() {
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setAll(true);
        when(permissionService.getDeptDataPermission(1L)).thenReturn(permission);
        assertDoesNotThrow(() -> scope.validateDeptInScope(20L));
        assertDoesNotThrow(() -> scope.validateUserInScope(2L));
        assertDoesNotThrow(() -> scope.validateClassInScope(99L));
    }

    private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class, action).getCode());
    }
}
