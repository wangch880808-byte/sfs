package cn.nus.zknu.module.capacity.dal.dataobject.indicator;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 培养指标 DO
 */
@TableName("capacity_indicator")
@Data
@EqualsAndHashCode(callSuper = true)
public class CapacityIndicatorDO extends BaseDO {

    private Long id;

    private String indicatorName;

    private String indicatorDetails;

    private Integer indicatorLevel;

    @TableField("parent_Indicator_id")
    private Long parentIndicatorId;

    private BigDecimal weight;

    /**
     * @see CommonStatusEnum
     */
    private Integer status;

}
