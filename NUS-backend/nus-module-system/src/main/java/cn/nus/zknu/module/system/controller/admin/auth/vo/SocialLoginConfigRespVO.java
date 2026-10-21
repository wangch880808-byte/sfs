package cn.nus.zknu.module.system.controller.admin.auth.vo;

/** 登录页可公开读取的社交登录配置，不包含客户端凭据。 */
public record SocialLoginConfigRespVO(boolean dingtalkEnabled) {
}
