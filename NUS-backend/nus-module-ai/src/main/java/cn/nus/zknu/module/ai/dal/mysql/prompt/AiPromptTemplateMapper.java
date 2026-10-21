package cn.nus.zknu.module.ai.dal.mysql.prompt;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.ai.dal.dataobject.prompt.AiPromptTemplateDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiPromptTemplateMapper extends BaseMapperX<AiPromptTemplateDO> {

    /** 启用版本；数据异常出现多个启用版本时取版本号最大的一个。 */
    default AiPromptTemplateDO selectActive(String businessCode) {
        return selectOne(new LambdaQueryWrapperX<AiPromptTemplateDO>()
                .eq(AiPromptTemplateDO::getBusinessCode, businessCode)
                .eq(AiPromptTemplateDO::getActive, true)
                .orderByDesc(AiPromptTemplateDO::getVersion)
                .last("LIMIT 1"));
    }

    default List<AiPromptTemplateDO> selectListByBusinessCode(String businessCode) {
        return selectList(new LambdaQueryWrapperX<AiPromptTemplateDO>()
                .eq(AiPromptTemplateDO::getBusinessCode, businessCode)
                .orderByDesc(AiPromptTemplateDO::getVersion));
    }

    default AiPromptTemplateDO selectLatest(String businessCode) {
        return selectOne(new LambdaQueryWrapperX<AiPromptTemplateDO>()
                .eq(AiPromptTemplateDO::getBusinessCode, businessCode)
                .orderByDesc(AiPromptTemplateDO::getVersion)
                .last("LIMIT 1"));
    }

    /** 停用同一业务除指定版本外的启用版本。 */
    default int deactivateOthers(String businessCode, Long keepId) {
        return update(new AiPromptTemplateDO(), new LambdaUpdateWrapper<AiPromptTemplateDO>()
                .set(AiPromptTemplateDO::getActive, false)
                .eq(AiPromptTemplateDO::getBusinessCode, businessCode)
                .eq(AiPromptTemplateDO::getActive, true)
                .ne(AiPromptTemplateDO::getId, keepId));
    }

    default int activate(Long id) {
        return update(new AiPromptTemplateDO(), new LambdaUpdateWrapper<AiPromptTemplateDO>()
                .set(AiPromptTemplateDO::getActive, true)
                .eq(AiPromptTemplateDO::getId, id));
    }

}
