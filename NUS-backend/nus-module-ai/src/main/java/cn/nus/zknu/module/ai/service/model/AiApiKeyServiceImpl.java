package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.ai.controller.admin.model.vo.apikey.AiApiKeyPageReqVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.apikey.AiApiKeySaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.mysql.model.AiApiKeyMapper;
import cn.nus.zknu.module.ai.dal.mysql.model.AiChatMapper;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.API_KEY_DISABLE;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.API_KEY_IN_USE;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.API_KEY_REQUIRED;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.API_KEY_NOT_EXISTS;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.MODEL_PLATFORM_MISMATCH;

/**
 * AI API 密钥 Service 实现类
 *
 * @author hyh
 */
@Service
@Validated
public class AiApiKeyServiceImpl implements AiApiKeyService {

    @Resource
    private AiApiKeyMapper apiKeyMapper;
    @Resource
    private AiChatMapper modelMapper;

    @Override
    public Long createApiKey(AiApiKeySaveReqVO createReqVO) {
        AiPlatformEnum.validatePlatform(createReqVO.getPlatform());
        if (StrUtil.isBlank(createReqVO.getApiKey())) {
            throw exception(API_KEY_REQUIRED);
        }
        // 插入
        AiApiKeyDO apiKey = BeanUtils.toBean(createReqVO, AiApiKeyDO.class);
        apiKeyMapper.insert(apiKey);
        // 返回
        return apiKey.getId();
    }

    @Override
    public void updateApiKey(AiApiKeySaveReqVO updateReqVO) {
        // 校验存在
        AiApiKeyDO existing = validateApiKeyExists(updateReqVO.getId());
        AiPlatformEnum.validatePlatform(updateReqVO.getPlatform());
        if (!existing.getPlatform().equals(updateReqVO.getPlatform())
                && modelMapper.selectCount(new LambdaQueryWrapper<AiModelDO>()
                .eq(AiModelDO::getKeyId, updateReqVO.getId())) > 0) {
            throw exception(MODEL_PLATFORM_MISMATCH);
        }
        // 更新
        AiApiKeyDO updateObj = BeanUtils.toBean(updateReqVO, AiApiKeyDO.class);
        if (StrUtil.isBlank(updateReqVO.getApiKey())) {
            updateObj.setApiKey(existing.getApiKey());
        }
        apiKeyMapper.updateById(updateObj);
    }

    @Override
    public void deleteApiKey(Long id) {
        // 校验存在
        validateApiKeyExists(id);
        if (modelMapper.selectCount(new LambdaQueryWrapper<AiModelDO>()
                .eq(AiModelDO::getKeyId, id)) > 0) {
            throw exception(API_KEY_IN_USE);
        }
        // 删除
        apiKeyMapper.deleteById(id);
    }

    private AiApiKeyDO validateApiKeyExists(Long id) {
        AiApiKeyDO apiKey = apiKeyMapper.selectById(id);
        if (apiKey == null) {
            throw exception(API_KEY_NOT_EXISTS);
        }
        return apiKey;
    }

    @Override
    public AiApiKeyDO getApiKey(Long id) {
        return apiKeyMapper.selectById(id);
    }

    @Override
    public AiApiKeyDO validateApiKey(Long id) {
        AiApiKeyDO apiKey = validateApiKeyExists(id);
        if (CommonStatusEnum.isDisable(apiKey.getStatus())) {
            throw exception(API_KEY_DISABLE);
        }
        return apiKey;
    }

    @Override
    public PageResult<AiApiKeyDO> getApiKeyPage(AiApiKeyPageReqVO pageReqVO) {
        return apiKeyMapper.selectPage(pageReqVO);
    }

    @Override
    public List<AiApiKeyDO> getApiKeyList() {
        return apiKeyMapper.selectList();
    }

    @Override
    public AiApiKeyDO getRequiredDefaultApiKey(String platform, Integer status) {
        AiApiKeyDO apiKey = apiKeyMapper.selectFirstByPlatformAndStatus(platform, status);
        if (apiKey == null) {
            throw exception(API_KEY_NOT_EXISTS);
        }
        return apiKey;
    }

    @Override
    public int migratePlaintextApiKeys() {
        List<Long> ids = apiKeyMapper.selectPlaintextApiKeyIds();
        int migrated = 0;
        for (Long id : ids) {
            AiApiKeyDO existing = apiKeyMapper.selectById(id);
            if (existing == null || StrUtil.isBlank(existing.getApiKey())) {
                continue;
            }
            AiApiKeyDO update = new AiApiKeyDO();
            update.setId(id);
            update.setApiKey(existing.getApiKey());
            apiKeyMapper.updateById(update);
            migrated++;
        }
        return migrated;
    }

}
