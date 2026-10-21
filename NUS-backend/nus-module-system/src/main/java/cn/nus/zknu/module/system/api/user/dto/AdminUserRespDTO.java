package cn.nus.zknu.module.system.api.user.dto;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import lombok.Data;

import java.util.Set;

/**
 * Admin 用户 Response DTO
 *
 * @author hyh
 */
@Data
public class AdminUserRespDTO {

    /**
     * 用户ID
     */
    private Long id;
    /** 登录账号。 */
    private String username;
    /**
     * 用户名称
     */
    private String nickname;
    /**
     * 用户类型
     * <p>
     * 枚举 {@link UserTypeEnum}
     */
    private Integer userType;
    /**
     * 帐号状态
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 部门ID
     */
    private Long deptId;
    /**
     * 岗位编号数组
     */
    private Set<Long> postIds;
    /**
     * 手机号码
     */
    private String mobile;
    /** 用户邮箱。 */
    private String email;
    /**
     * 用户头像
     */
    private String avatar;

}
