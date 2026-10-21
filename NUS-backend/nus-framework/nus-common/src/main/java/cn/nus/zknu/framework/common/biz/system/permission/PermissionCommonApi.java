package cn.nus.zknu.framework.common.biz.system.permission;

import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;

import java.util.Set;

/**
 * 权限 API 接口
 *
 * @author hyh
 */
public interface PermissionCommonApi {

    /**
     * 判断是否有权限，任一一个即可
     *
     * @param userId      用户编号
     * @param permissions 权限
     * @return 是否
     */
    boolean hasAnyPermissions(Long userId, String... permissions);

    /**
     * 判断是否有角色，任一一个即可
     *
     * @param userId 用户编号
     * @param roles  角色数组
     * @return 是否
     */
    boolean hasAnyRoles(Long userId, String... roles);

    /**
     * 获得小程序令牌可以使用的角色编码。
     * <p>
     * 小程序令牌只代表学生身份，{@code @ss.hasRole} 只对这些角色生效。角色编码由业务模块按参数配置提供，
     * 框架层不写死。
     *
     * @return 角色编码集合
     */
    Set<String> getMiniProgramRoleCodes();

    /**
     * 获得登陆用户的部门数据权限
     *
     * @param userId 用户编号
     * @return 部门数据权限
     */
    DeptDataPermissionRespDTO getDeptDataPermission(Long userId);

}
