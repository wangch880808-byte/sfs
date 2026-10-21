package cn.nus.zknu.module.ai.controller.admin.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - AI 调用每日汇总 Response VO")
@Data
public class AiCallLogDailySummaryRespVO {

    @Schema(description = "日期")
    private LocalDate date;

    @Schema(description = "业务码")
    private String businessCode;

    @Schema(description = "业务名称")
    private String businessName;

    @Schema(description = "调用次数")
    private Long callCount;

    @Schema(description = "失败次数")
    private Long failedCount;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

}
