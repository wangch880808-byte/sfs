package cn.nus.zknu.module.capacity.controller.admin.indicator.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 培养指标新增/修改 Request VO")
@Data
public class CapacityIndicatorSaveReqVO {

    @Schema(description = "指标 ID", example = "1024")
    private Long id;

    @Schema(description = "指标名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "毕业要求 1")
    @NotBlank(message = "指标名称不能为空")
    @Size(max = 255, message = "指标名称长度不能超过 255 个字符")
    private String indicatorName;

    @Schema(description = "指标详情", example = "能够掌握相关理论并完成应用")
    @Size(max = 255, message = "指标详情长度不能超过 255 个字符")
    private String indicatorDetails;

    @Schema(description = "指标等级（1-一级，2-二级）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "指标级别不能为空")
    private Integer indicatorLevel;

    @Schema(description = "父指标 ID，一级指标固定为 0，二级指标必须选择一级指标", example = "1")
    private Long parentIndicatorId;

    @Schema(description = "权重", requiredMode = Schema.RequiredMode.REQUIRED, example = "25.00")
    @NotNull(message = "权重不能为空")
    @DecimalMin(value = "0", message = "权重不能小于 0")
    @DecimalMax(value = "100", message = "权重不能超过 100")
    private BigDecimal weight;

    @Schema(description = "状态（0正常 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

}
