package cn.nus.zknu.module.capacity.api.indicator;

import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;

import java.util.Collection;
import java.util.List;

/**
 * 培养指标 API：供其他模块读取指标，不直接访问 capacity 的表与 Mapper。
 */
public interface CapacityIndicatorApi {

    /**
     * 获得指标（不含已删除）。
     *
     * @return 不存在时返回 null
     */
    CapacityIndicatorRespDTO getIndicator(Long id);

    /** 批量获得指标（不含已删除），不存在的 ID 忽略。 */
    List<CapacityIndicatorRespDTO> getIndicatorList(Collection<Long> ids);

    /** 全部指标（不含已删除），按级别、上级指标、ID 升序，用于组装指标树。 */
    List<CapacityIndicatorRespDTO> getIndicatorTreeList();

}
