package cn.nus.zknu.module.system.api.permission;

import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.permission.RoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限 API 实现类
 *
 * @author hyh
 */
@Service
public class PermissionApiImpl implements PermissionApi {

    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleService roleService;
    @Resource
    private EduRoleApi eduRoleApi;

    @Override
    public Set<Long> getUserRoleIdListByRoleIds(Collection<Long> roleIds) {
        return permissionService.getUserRoleIdListByRoleId(roleIds);
    }

    @Override
    public Set<Long> getUserIdListByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> roleIds = roleService.getRoleListByStatus(Set.of(CommonStatusEnum.ENABLE.getStatus())).stream()
                .filter(role -> roleCodes.contains(role.getCode()))
                .map(RoleDO::getId)
                .collect(Collectors.toSet());
        return roleIds.isEmpty() ? Collections.emptySet()
                : permissionService.getUserRoleIdListByRoleId(roleIds);
    }

    @Override
    public Set<Long> getEnableUserIdListByPermission(String permission) {
        return permissionService.getEnableUserIdListByPermission(permission);
    }

    @Override
    public Set<String> getMiniProgramRoleCodes() {
        // 小程序令牌只代表学生身份；学生角色编码按参数配置 edu.role.student-code
        return Set.of(eduRoleApi.getStudentRoleCode());
    }

    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        return permissionService.hasAnyPermissions(userId, permissions);
    }

    @Override
    public boolean hasAnyRoles(Long userId, String... roles) {
        return permissionService.hasAnyRoles(userId, roles);
    }

    @Override
    public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
        return permissionService.getDeptDataPermission(userId);
    }

}
