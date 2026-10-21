package cn.nus.zknu.module.system.controller.admin.socail.vo.client;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.validation.InEnum;
import cn.nus.zknu.module.system.enums.social.SocialTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Schema(description = "管理后台 - 社交客户端创建/修改 Request VO")
@Data
public class SocialClientSaveReqVO {

    @Schema(description = "编号", example = "27162")
    private Long id;

    @Schema(description = "应用名", requiredMode = Schema.RequiredMode.REQUIRED, example = "nus商城")
    @NotNull(message = "应用名不能为空")
    private String name;

    @Schema(description = "社交平台的类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "31")
    @NotNull(message = "社交平台的类型不能为空")
    @InEnum(SocialTypeEnum.class)
    private Integer socialType;

    @Schema(description = "用户类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "用户类型不能为空")
    @InEnum(UserTypeEnum.class)
    private Integer userType;

    @Schema(description = "客户端编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "wwd411c69a39ad2e54")
    @NotNull(message = "客户端编号不能为空")
    private String clientId;

    @Schema(description = "客户端密钥；创建时必填，更新时留空表示保持原密钥", example = "peter")
    private String clientSecret;

    @Schema(description = "publicKey 公钥", example = "2000045")
    private String publicKey;

    @Schema(description = "平台扩展配置")
    private Map<String, String> config;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

    @AssertTrue(message = "publicKey 不能为空")
    @JsonIgnore
    public boolean isPublicKeyValid() {
        // 如果是支付宝，必须填写 publicKey 属性
        return !Objects.equals(socialType, SocialTypeEnum.ALIPAY_MINI_PROGRAM.getType())
                || !StrUtil.isEmpty(publicKey);
    }

    @AssertTrue(message = "新建社交客户端时，客户端密钥不能为空")
    @JsonIgnore
    public boolean isClientSecretValid() {
        return id != null || StrUtil.isNotBlank(clientSecret);
    }

    @AssertTrue(message = "微信小程序扩展配置不正确")
    @JsonIgnore
    public boolean isWechatMiniProgramConfigValid() {
        if (!Objects.equals(socialType, SocialTypeEnum.WECHAT_MINI_PROGRAM.getType())) {
            return true;
        }
        if (config == null || StrUtil.isBlank(config.get("qrPage"))) {
            return false;
        }
        return Set.of("develop", "trial", "release").contains(config.get("envVersion"));
    }

    @AssertTrue(message = "微信小程序仅支持管理员用户类型")
    @JsonIgnore
    public boolean isWechatMiniProgramUserTypeValid() {
        return !Objects.equals(socialType, SocialTypeEnum.WECHAT_MINI_PROGRAM.getType())
                || Objects.equals(userType, UserTypeEnum.ADMIN.getValue());
    }

}
