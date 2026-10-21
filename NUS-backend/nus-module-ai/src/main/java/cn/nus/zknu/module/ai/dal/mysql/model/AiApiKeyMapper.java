package cn.nus.zknu.module.ai.dal.mysql.model;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.framework.mybatis.core.query.QueryWrapperX;
import cn.nus.zknu.module.ai.controller.admin.model.vo.apikey.AiApiKeyPageReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiApiKeyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI API 密钥 Mapper
 *
 * @author hyh
 */
@Mapper
public interface AiApiKeyMapper extends BaseMapperX<AiApiKeyDO> {

    default PageResult<AiApiKeyDO> selectPage(AiApiKeyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AiApiKeyDO>()
                .likeIfPresent(AiApiKeyDO::getName, reqVO.getName())
                .eqIfPresent(AiApiKeyDO::getPlatform, reqVO.getPlatform())
                .eqIfPresent(AiApiKeyDO::getStatus, reqVO.getStatus())
                .orderByDesc(AiApiKeyDO::getId));
    }

    default AiApiKeyDO selectFirstByPlatformAndStatus(String platform, Integer status) {
        return selectOne(new QueryWrapperX<AiApiKeyDO>()
                .eq("platform", platform)
                .eq("status", status)
                .limitN(1)
                .orderByAsc("id"));
    }

    @Select("SELECT id FROM ai_api_key WHERE deleted = 0 AND api_key IS NOT NULL "
            + "AND api_key <> '' AND api_key NOT LIKE 'ENC$%'")
    List<Long> selectPlaintextApiKeyIds();

}
