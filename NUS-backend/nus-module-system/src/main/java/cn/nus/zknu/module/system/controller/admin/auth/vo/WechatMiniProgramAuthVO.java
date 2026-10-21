package cn.nus.zknu.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信小程序绑定与扫码登录 VO。
 */
public final class WechatMiniProgramAuthVO {

    private WechatMiniProgramAuthVO() {
    }

    @Data
    public static class ConfigRespVO {
        private Boolean enabled;
    }

    @Data
    public static class QrCreateRespVO {
        private String ticket;
        private String browserToken;
        private String purpose;
        private String qrCodeDataUrl;
        private LocalDateTime expiresAt;
    }

    @Data
    public static class QrStatusReqVO {
        @NotBlank
        private String ticket;
        @NotBlank
        private String browserToken;
    }

    @Data
    public static class QrStatusRespVO {
        private String state;
        private String purpose;
        private String accountNickname;
        private LocalDateTime expiresAt;
    }

    @Data
    public static class QrExchangeReqVO extends QrStatusReqVO {
    }

    @Data
    public static class SessionReqVO {
        @NotBlank(message = "微信登录凭证不能为空")
        private String code;
    }

    @Data
    public static class SessionRespVO {
        private String miniSessionToken;
        private Boolean bound;
        private AuthLoginRespVO login;
        private AccountVO account;
        private CapabilitiesVO capabilities;
    }

    @Data
    public static class QrActionReqVO {
        @NotBlank
        private String ticket;
        @NotBlank
        private String miniSessionToken;
    }

    @Data
    public static class BindingRespVO {
        private Boolean bound;
        private String openidMasked;
    }

    @Data
    public static class AccountVO {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
    }

    @Data
    public static class CapabilitiesVO {
        @Schema(description = "是否可以确认网页登录")
        private Boolean scanLogin;
        @Schema(description = "是否可以使用学生作业能力")
        private Boolean studentHomework;
    }

}
