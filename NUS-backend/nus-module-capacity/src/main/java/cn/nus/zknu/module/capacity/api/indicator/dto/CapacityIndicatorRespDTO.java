package cn.nus.zknu.module.capacity.api.indicator.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 培养指标 Response DTO
 */
@Data
public class CapacityIndicatorRespDTO {

    private Long id;
    private String indicatorName;
    private String indicatorDetails;
    /** 指标级别：1 一级，2 二级 */
    private Integer indicatorLevel;
    /** 上级指标 ID（二级指标才有） */
    private Long parentIndicatorId;
    private BigDecimal weight;
    /** 状态：0 正常，1 停用 */
    private Integer status;

}
