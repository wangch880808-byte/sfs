package cn.nus.zknu.module.capacity.service.indicator;

import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorBatchCreateReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorResetWeightReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;

import java.util.List;

public interface CapacityIndicatorService {

    Long createIndicator(CapacityIndicatorSaveReqVO createReqVO);

    void batchCreateIndicators(CapacityIndicatorBatchCreateReqVO batchCreateReqVO);

    void updateIndicator(CapacityIndicatorSaveReqVO updateReqVO);

    void resetIndicatorWeights(CapacityIndicatorResetWeightReqVO resetWeightReqVO);

    void deleteIndicator(Long id);

    CapacityIndicatorDO getIndicator(Long id);

    List<CapacityIndicatorDO> getIndicatorList();

}
