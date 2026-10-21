package cn.nus.zknu.framework.security.core.service;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import lombok.AllArgsConstructor;

import java.util.Arrays;

import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 默认的 {@link SecurityFrameworkService} 实现类
 *
 * @author hyh
 */
@AllArgsConstructor
public class SecurityFrameworkServiceImpl implements SecurityFrameworkService {

    private static final String MINI_PROGRAM_SCOPE = "miniapp:student";
    private static final java.util.Set<String> MINI_PROGRAM_PERMISSIONS = java.util.Set.of(
            "edu:student-homework:query", "edu:student-homework:submit");

    private final PermissionCommonApi permissionApi;

    @Override
    public boolean hasPermission(String permission) {
        return hasAnyPermissions(permission);
    }

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        Long userId = getLoginUserId();
        if (userId == null) {
            return false;
        }
        if (isMiniProgramToken()) {
            permissions = Arrays.stream(permissions).filter(MINI_PROGRAM_PERMISSIONS::contains)
                    .toArray(String[]::new);
            if (permissions.length == 0) {
                return false;
            }
        }
        return permissionApi.hasAnyPermissions(userId, permissions);
    }

    @Override
    public boolean hasRole(String role) {
        return hasAnyRoles(role);
    }

    @Override
    public boolean hasAnyRoles(String... roles) {
        Long userId = getLoginUserId();
        if (userId == null) {
            return false;
        }
        if (isMiniProgramToken()) {
            java.util.Set<String> allowedRoles = permissionApi.getMiniProgramRoleCodes();
            roles = Arrays.stream(roles).filter(role -> allowedRoles != null && allowedRoles.contains(role))
                    .toArray(String[]::new);
            if (roles.length == 0) {
                return false;
            }
        }
        return permissionApi.hasAnyRoles(userId, roles);
    }

    @Override
    public boolean hasScope(String scope) {
        return hasAnyScopes(scope);
    }

    @Override
    public boolean hasAnyScopes(String... scope) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            return false;
        }
        return CollUtil.containsAny(user.getScopes(), Arrays.asList(scope));
    }

    private boolean isMiniProgramToken() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        return user != null && CollUtil.contains(user.getScopes(), MINI_PROGRAM_SCOPE);
    }

}
