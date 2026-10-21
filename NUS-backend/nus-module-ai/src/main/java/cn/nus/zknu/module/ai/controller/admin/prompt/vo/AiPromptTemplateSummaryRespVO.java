package cn.nus.zknu.module.ai.controller.admin.prompt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 提示词概览 Response VO")
@Data
public class AiPromptTemplateSummaryRespVO {

    @Schema(description = "提示词业务码", example = "HOMEWORK_REVIEW")
    private String businessCode;

    @Schema(description = "提示词名称", example = "任务 AI 初评")
    private String name;

    @Schema(description = "所属 AI 业务名称", example = "任务 AI 初评")
    private String businessName;

    @Schema(description = "启用版本编号；为空表示使用内置默认内容")
    private Long activeId;

    @Schema(description = "启用版本号")
    private Integer activeVersion;

    @Schema(description = "最新版本号")
    private Integer latestVersion;

    @Schema(description = "当前生效内容")
    private String content;

    @Schema(description = "内置默认内容")
    private String defaultContent;

    @Schema(description = "是否使用内置默认内容（没有启用版本）")
    private Boolean usingDefault;

    @Schema(description = "生效内容是否与内置默认内容一致")
    private Boolean sameAsDefault;

    @Schema(description = "启用版本的保存时间")
    private LocalDateTime activeTime;

}
