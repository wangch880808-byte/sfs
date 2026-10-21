package cn.nus.zknu.module.ai.controller.admin.prompt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 新建 AI 提示词版本 Request VO")
@Data
public class AiPromptTemplateCreateReqVO {

    @Schema(description = "提示词业务码", requiredMode = Schema.RequiredMode.REQUIRED, example = "HOMEWORK_REVIEW")
    @NotBlank(message = "提示词业务不能为空")
    private String businessCode;

    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "提示词内容不能为空")
    private String content;

    @Schema(description = "版本说明")
    @Size(max = 255, message = "版本说明不能超过 255 字")
    private String remark;

    @Schema(description = "保存后是否立即启用", example = "true")
    private Boolean activate;

}
