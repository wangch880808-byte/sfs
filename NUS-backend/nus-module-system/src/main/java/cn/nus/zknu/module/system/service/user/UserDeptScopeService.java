package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.util.DataPermissionUtils;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.dal.mysql.user.UserClassScopeMapper;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** system 模块的写入范围校验，避免依赖 edu 模块。 */
@Service
public class UserDeptScopeService {

    @Resource
    @Lazy
    private PermissionService permissionService;
    @Resource
    private AdminUserMapper userMapper;
    @Resource
    private UserClassScopeMapper classScopeMapper;

    public DeptDataPermissionRespDTO getDeptDataPermission() {
        Long userId = getLoginUserId();
        return UserDeptScopeRequestCache.get(UserDeptScopeService.class.getName() + ".permission", userId,
                () -> computeDeptDataPermission(userId));
    }

    private DeptDataPermissionRespDTO computeDeptDataPermission(Long userId) {
        if (userId == null) {
            // 后台任务没有登录用户，与脚手架部门规则保持一致。
            DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
            permission.setAll(true);
            return permission;
        }
        AdminUserDO user = DataPermissionUtils.executeIgnore(() -> userMapper.selectById(userId));
        if (user == null) {
            throw exception(FORBIDDEN);
        }
        if (user.getDeptId() == null && permissionService.hasAnyRoles(userId, "college_admin")) {
            throw exception(FORBIDDEN).setMessage("账号未设置所属学院，请联系管理员");
        }
        DeptDataPermissionRespDTO permission = permissionService.getDeptDataPermission(userId);
        if (permission == null) {
            throw exception(FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(permission.getAll())) {
            if (user.getDeptId() == null) {
                throw exception(FORBIDDEN).setMessage("账号未设置所属学院，请联系管理员");
            }
        }
        return permission;
    }

    public boolean isRestricted() {
        return !Boolean.TRUE.equals(getDeptDataPermission().getAll());
    }

    public boolean isDeptInScope(Long deptId) {
        DeptDataPermissionRespDTO permission = getDeptDataPermission();
        return Boolean.TRUE.equals(permission.getAll()) || (deptId != null
                && permission.getDeptIds() != null && permission.getDeptIds().contains(deptId));
    }

    public void validateDeptInScope(Long deptId) {
        if (!isDeptInScope(deptId)) {
            throw exception(FORBIDDEN);
        }
    }

    /** 仅返回是否在范围内，不向调用方暴露外院账号资料。 */
    public boolean isUsernameInScope(String username) {
        if (!isRestricted() || username == null) {
            return true;
        }
        AdminUserDO user = DataPermissionUtils.executeIgnore(() -> userMapper.selectByUsername(username));
        return user == null || isDeptInScope(user.getDeptId());
    }

    public void validateUserInScope(Long userId) {
        if (!isRestricted()) {
            return;
        }
        AdminUserDO user = userId == null ? null
                : DataPermissionUtils.executeIgnore(() -> userMapper.selectById(userId));
        if (user == null) {
            throw exception(FORBIDDEN);
        }
        validateDeptInScope(user.getDeptId());
    }

    public void validateClassInScope(Long classId) {
        if (isRestricted()) {
            Long deptId = DataPermissionUtils.executeIgnore(() -> classScopeMapper.selectDeptId(classId));
            validateDeptInScope(deptId);
        }
    }

    public void validateAffectedRows(int affectedRows) {
        if (affectedRows == 0) {
            throw exception(FORBIDDEN);
        }
    }
}
