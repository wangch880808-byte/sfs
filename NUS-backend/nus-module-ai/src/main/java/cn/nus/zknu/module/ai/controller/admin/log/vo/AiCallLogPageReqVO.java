package cn.nus.zknu.module.ai.controller.admin.log.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - AI 调用日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiCallLogPageReqVO extends PageParam {

    @Schema(description = "业务码", example = "HOMEWORK_REVIEW")
    private String businessCode;

    @Schema(description = "模型编号")
    private Long modelId;

    @Schema(description = "模型标识，模糊匹配", example = "deepseek")
    private String modelName;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "发起人编号")
    private Long operatorId;

    @Schema(description = "调用时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
