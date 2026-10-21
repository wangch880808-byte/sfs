package cn.nus.zknu.module.system.api.permission;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;

import java.util.Collection;
import java.util.Set;

/**
 * 权限 API 接口
 *
 * @author hyh
 */
public interface PermissionApi extends PermissionCommonApi {

    /**
     * 获得拥有多个角色的用户编号集合
     *
     * @param roleIds 角色编号集合
     * @return 用户编号集合
     */
    Set<Long> getUserRoleIdListByRoleIds(Collection<Long> roleIds);

    /**
     * 获得指定角色编码下的用户编号集合。
     */
    Set<Long> getUserIdListByRoleCodes(Collection<String> roleCodes);

    /**
     * 获得持有指定权限标识、且账号为开启状态的用户编号集合（含超级管理员）。
     * 用于“发给有某项权限的人”的通知，例如公共课待审批通知发给持有审批权限的人。
     */
    Set<Long> getEnableUserIdListByPermission(String permission);

}
