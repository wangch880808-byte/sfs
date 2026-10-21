package cn.nus.zknu.module.capacity.controller.admin.indicator.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 培养指标批量重设权重 Request VO")
@Data
public class CapacityIndicatorResetWeightReqVO {

    @Schema(description = "指标等级（1-一级，2-二级）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "指标级别不能为空")
    private Integer indicatorLevel;

    @Schema(description = "父指标 ID，重设一级时固定为 0，重设二级时为当前一级指标 ID", example = "1")
    private Long parentIndicatorId;

    @Schema(description = "指标权重列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "指标权重列表不能为空")
    @Valid
    private List<IndicatorWeightItem> indicators;

    @Schema(description = "指标权重项")
    @Data
    public static class IndicatorWeightItem {

        @Schema(description = "指标 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        @NotNull(message = "指标 ID 不能为空")
        private Long id;

        @Schema(description = "权重", requiredMode = Schema.RequiredMode.REQUIRED, example = "25.00")
        @NotNull(message = "权重不能为空")
        @DecimalMin(value = "0", message = "权重不能小于 0")
        @DecimalMax(value = "100", message = "权重不能超过 100")
        private BigDecimal weight;

    }

}
