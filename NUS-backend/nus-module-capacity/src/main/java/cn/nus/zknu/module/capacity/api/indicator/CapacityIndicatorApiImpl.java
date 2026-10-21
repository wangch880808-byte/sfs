package cn.nus.zknu.module.capacity.api.indicator;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import cn.nus.zknu.module.capacity.dal.mysql.indicator.CapacityIndicatorMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 培养指标 API 实现类
 */
@Service
public class CapacityIndicatorApiImpl implements CapacityIndicatorApi {

    @Resource
    private CapacityIndicatorMapper indicatorMapper;

    @Override
    public CapacityIndicatorRespDTO getIndicator(Long id) {
        if (id == null) {
            return null;
        }
        CapacityIndicatorDO indicator = indicatorMapper.selectById(id);
        return indicator == null ? null : BeanUtils.toBean(indicator, CapacityIndicatorRespDTO.class);
    }

    @Override
    public List<CapacityIndicatorRespDTO> getIndicatorList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return BeanUtils.toBean(indicatorMapper.selectByIds(ids), CapacityIndicatorRespDTO.class);
    }

    @Override
    public List<CapacityIndicatorRespDTO> getIndicatorTreeList() {
        return BeanUtils.toBean(indicatorMapper.selectListForTree(), CapacityIndicatorRespDTO.class);
    }

}
