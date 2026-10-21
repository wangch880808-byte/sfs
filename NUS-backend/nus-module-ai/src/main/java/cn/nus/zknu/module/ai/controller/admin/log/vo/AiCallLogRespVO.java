package cn.nus.zknu.module.ai.controller.admin.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 调用日志 Response VO")
@Data
public class AiCallLogRespVO {

    private Long id;

    @Schema(description = "业务码")
    private String businessCode;

    @Schema(description = "业务名称")
    private String businessName;

    private Long modelId;

    @Schema(description = "模型标识")
    private String modelName;

    private String platform;

    @Schema(description = "业务对象编号")
    private String bizId;

    private Long operatorId;

    @Schema(description = "发起人")
    private String operatorName;

    @Schema(description = "是否后台任务发起")
    private Boolean background;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long durationMs;

    private Boolean success;

    @Schema(description = "错误分类")
    private String errorType;

    @Schema(description = "脱敏后的错误摘要")
    private String errorMessage;

    private LocalDateTime createTime;

}
