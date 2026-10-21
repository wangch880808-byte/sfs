package cn.nus.zknu.module.system.service.auth;

import cn.nus.zknu.module.system.controller.admin.auth.vo.*;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import jakarta.validation.Valid;

/**
 * 管理后台的认证 Service 接口
 * <p>
 * 提供用户的登录、登出的能力
 *
 * @author hyh
 */
public interface AdminAuthService {

    /**
     * 验证账号 + 密码。如果通过，则返回用户
     *
     * @param username 账号
     * @param password 密码
     * @return 用户
     */
    AdminUserDO authenticate(String username, String password);

    /**
     * 账号登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO login(@Valid AuthLoginReqVO reqVO);

    /**
     * 基于 token 退出登录
     *
     * @param token   token
     * @param logType 登出类型
     */
    void logout(String token, Integer logType);

    /**
     * 社交快捷登录，使用 code 授权码
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO socialLogin(@Valid AuthSocialLoginReqVO reqVO);

    /**
     * 已完成可信身份校验的用户登录。
     *
     * @param userId 用户编号
     * @return 登录结果
     */
    AuthLoginRespVO loginByTrustedUserId(Long userId);

    /** 为受限客户端签发带授权范围的可信令牌。 */
    AuthLoginRespVO loginByTrustedUserId(Long userId, String clientId, java.util.List<String> scopes);

    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    AuthLoginRespVO refreshToken(String refreshToken);

}
