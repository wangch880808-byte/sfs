package cn.nus.zknu.module.system.service.permission;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.nus.zknu.module.system.dal.dataobject.permission.UserRoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.nus.zknu.module.system.dal.mysql.permission.UserRoleMapper;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * “持有某权限的人”的口径：开启的角色经角色-菜单持有，或是超级管理员；只取开启的账号。
 */
class PermissionServiceImplPermissionHolderTest extends BaseMockitoUnitTest {

    private static final String AUDIT = "capacity:course:audit";

    @InjectMocks
    private PermissionServiceImpl permissionService;
    @Mock
    private RoleMenuMapper roleMenuMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RoleService roleService;
    @Mock
    private MenuService menuService;
    @Mock
    private AdminUserService userService;

    @Test
    void holders_includeGrantedEnabledRolesAndSuperAdminAndOnlyActiveUsers() {
        when(menuService.getMenuIdListByPermissionFromCache(AUDIT)).thenReturn(List.of(5129L));
        when(roleMenuMapper.selectListByMenuId(5129L)).thenReturn(List.of(roleMenu(159L), roleMenu(170L)));
        // 159 学校教务管理员（已授权、开启）；170 已授权但角色停用不在开启列表里；1 超级管理员未逐项授权；161 教师未授权
        when(roleService.getRoleListByStatus(Set.of(CommonStatusEnum.ENABLE.getStatus())))
                .thenReturn(List.of(role(1L, "super_admin"), role(159L, "school_admin"), role(161L, "teacher")));
        when(userRoleMapper.selectListByRoleIds(anyCollection())).thenReturn(List.of(userRole(11L), userRole(12L), userRole(13L)));
        when(userService.getUserList(anyCollection())).thenReturn(List.of(
                user(11L, CommonStatusEnum.ENABLE.getStatus()),
                user(12L, CommonStatusEnum.DISABLE.getStatus()),
                user(13L, CommonStatusEnum.ENABLE.getStatus())));

        Set<Long> holders = permissionService.getEnableUserIdListByPermission(AUDIT);

        assertEquals(Set.of(11L, 13L), holders);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> roleIds = ArgumentCaptor.forClass(Collection.class);
        verify(userRoleMapper).selectListByRoleIds(roleIds.capture());
        assertEquals(Set.of(1L, 159L), Set.copyOf(roleIds.getValue()));
    }

    @Test
    void noHolderRoles_returnsEmptyWithoutQueryingUsers() {
        when(menuService.getMenuIdListByPermissionFromCache(AUDIT)).thenReturn(List.of());
        when(roleService.getRoleListByStatus(any())).thenReturn(List.of(role(161L, "teacher")));

        assertTrue(permissionService.getEnableUserIdListByPermission(AUDIT).isEmpty());
        verifyNoInteractions(userRoleMapper, userService);
    }

    @Test
    void blankPermission_returnsEmpty() {
        assertTrue(permissionService.getEnableUserIdListByPermission(" ").isEmpty());
        verifyNoInteractions(menuService, roleService);
    }

    private static RoleMenuDO roleMenu(Long roleId) {
        RoleMenuDO roleMenu = new RoleMenuDO();
        roleMenu.setRoleId(roleId);
        return roleMenu;
    }

    private static RoleDO role(Long id, String code) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        role.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return role;
    }

    private static UserRoleDO userRole(Long userId) {
        UserRoleDO userRole = new UserRoleDO();
        userRole.setUserId(userId);
        return userRole;
    }

    private static AdminUserDO user(Long id, Integer status) {
        AdminUserDO user = new AdminUserDO();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

}
