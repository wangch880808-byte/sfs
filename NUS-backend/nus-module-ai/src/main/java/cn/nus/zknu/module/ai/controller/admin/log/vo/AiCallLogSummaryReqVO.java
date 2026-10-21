package cn.nus.zknu.module.ai.controller.admin.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - AI 调用每日汇总 Request VO")
@Data
public class AiCallLogSummaryReqVO {

    @Schema(description = "业务码")
    private String businessCode;

    @Schema(description = "调用时间范围；不传时取最近 7 天，最长 93 天")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
