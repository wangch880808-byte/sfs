package cn.nus.zknu.module.ai.dal.mysql.model;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiBusinessModelBindingDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiBusinessModelBindingMapper extends BaseMapperX<AiBusinessModelBindingDO> {

    default AiBusinessModelBindingDO selectByBusinessCode(String businessCode) {
        return selectOne(AiBusinessModelBindingDO::getBusinessCode, businessCode);
    }

}
