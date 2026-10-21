package cn.nus.zknu.module.capacity.dal.mysql.indicator;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CapacityIndicatorMapper extends BaseMapperX<CapacityIndicatorDO> {

    default List<CapacityIndicatorDO> selectListForTree() {
        return selectList(new LambdaQueryWrapperX<CapacityIndicatorDO>()
                .orderByAsc(CapacityIndicatorDO::getIndicatorLevel)
                .orderByAsc(CapacityIndicatorDO::getParentIndicatorId)
                .orderByAsc(CapacityIndicatorDO::getId));
    }

    default List<CapacityIndicatorDO> selectListByIndicatorLevel(Integer indicatorLevel) {
        return selectList(new LambdaQueryWrapperX<CapacityIndicatorDO>()
                .eq(CapacityIndicatorDO::getIndicatorLevel, indicatorLevel)
                .orderByAsc(CapacityIndicatorDO::getId));
    }

    default List<CapacityIndicatorDO> selectListByParentIndicatorId(Long parentIndicatorId) {
        return selectList(new LambdaQueryWrapperX<CapacityIndicatorDO>()
                .eq(CapacityIndicatorDO::getParentIndicatorId, parentIndicatorId)
                .orderByAsc(CapacityIndicatorDO::getId));
    }

    default Long selectCountByParentIndicatorId(Long parentIndicatorId) {
        return selectCount(CapacityIndicatorDO::getParentIndicatorId, parentIndicatorId);
    }

}
