package cn.nus.zknu.module.capacity.controller.admin.indicator.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 培养指标信息 Response VO")
@Data
public class CapacityIndicatorRespVO {

    @Schema(description = "指标 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "指标名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "毕业要求 1")
    private String indicatorName;

    @Schema(description = "指标详情", example = "能够掌握相关理论并完成应用")
    private String indicatorDetails;

    @Schema(description = "指标等级（1-一级，2-二级）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer indicatorLevel;

    @Schema(description = "父指标 ID", example = "1")
    private Long parentIndicatorId;

    @Schema(description = "权重", requiredMode = Schema.RequiredMode.REQUIRED, example = "25.00")
    private BigDecimal weight;

    @Schema(description = "状态（0正常 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
