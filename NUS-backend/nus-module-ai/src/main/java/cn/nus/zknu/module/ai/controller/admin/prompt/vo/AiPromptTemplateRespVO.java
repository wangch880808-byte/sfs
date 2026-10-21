package cn.nus.zknu.module.ai.controller.admin.prompt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 提示词版本 Response VO")
@Data
public class AiPromptTemplateRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "提示词业务码")
    private String businessCode;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "是否启用")
    private Boolean active;

    @Schema(description = "版本说明")
    private String remark;

    @Schema(description = "保存人")
    private String creatorName;

    @Schema(description = "保存时间")
    private LocalDateTime createTime;

}
