package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.ai.controller.admin.model.vo.model.AiModelPageReqVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.model.AiModelSaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiBusinessModelBindingDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.mysql.model.AiChatMapper;
import cn.nus.zknu.module.ai.dal.mysql.model.AiBusinessModelBindingMapper;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import cn.nus.zknu.module.ai.framework.ai.core.governance.AiCallGovernor;
import cn.nus.zknu.module.ai.framework.ai.core.model.AiModelFactory;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.*;

/**
 * AI 模型 Service 实现类
 */
@Service
@Validated
public class AiModelServiceImpl implements AiModelService {

    @Resource
    private AiApiKeyService apiKeyService;
    @Resource
    private AiChatMapper modelMapper;
    @Resource
    private AiModelFactory modelFactory;
    @Resource
    private AiBusinessModelBindingMapper bindingMapper;
    @Resource
    private AiCallGovernor callGovernor;

    @Override
    public Long createModel(AiModelSaveReqVO createReqVO) {
        AiPlatformEnum.validatePlatform(createReqVO.getPlatform());
        validateKeyPlatform(createReqVO.getKeyId(), createReqVO.getPlatform());
        AiModelDO model = BeanUtils.toBean(createReqVO, AiModelDO.class);
        modelMapper.insert(model);
        return model.getId();
    }

    @Override
    public void updateModel(AiModelSaveReqVO updateReqVO) {
        validateModelExists(updateReqVO.getId());
        AiPlatformEnum.validatePlatform(updateReqVO.getPlatform());
        validateKeyPlatform(updateReqVO.getKeyId(), updateReqVO.getPlatform());
        modelMapper.updateById(BeanUtils.toBean(updateReqVO, AiModelDO.class));
    }

    @Override
    public void deleteModel(Long id) {
        validateModelExists(id);
        if (bindingMapper.selectCount(AiBusinessModelBindingDO::getModelId, id) > 0) {
            throw exception(MODEL_IN_USE);
        }
        modelMapper.deleteById(id);
    }

    private AiModelDO validateModelExists(Long id) {
        AiModelDO model = modelMapper.selectById(id);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
        return model;
    }

    @Override
    public AiModelDO getModel(Long id) {
        return modelMapper.selectById(id);
    }

    @Override
    public AiModelDO getRequiredDefaultModel(Integer type) {
        AiModelDO model = modelMapper.selectFirstByStatus(type, CommonStatusEnum.ENABLE.getStatus());
        if (model == null) {
            throw exception(MODEL_DEFAULT_NOT_EXISTS);
        }
        return model;
    }

    @Override
    public PageResult<AiModelDO> getModelPage(AiModelPageReqVO pageReqVO) {
        return modelMapper.selectPage(pageReqVO);
    }

    @Override
    public AiModelDO validateModel(Long id) {
        AiModelDO model = validateModelExists(id);
        if (CommonStatusEnum.isDisable(model.getStatus())) {
            throw exception(MODEL_DISABLE, model.getName());
        }
        return model;
    }

    @Override
    public List<AiModelDO> getModelListByStatusAndType(Integer status, Integer type, String platform) {
        return modelMapper.selectListByStatusAndType(status, type, platform);
    }

    @Override
    public ChatModel getChatModel(Long id) {
        AiModelDO model = validateModel(id);
        AiApiKeyDO apiKey = apiKeyService.validateApiKey(model.getKeyId());
        validatePlatformMatch(model, apiKey);
        AiPlatformEnum platform = AiPlatformEnum.validatePlatform(apiKey.getPlatform());
        // 统一加上熔断、额度和调用日志
        return callGovernor.govern(modelFactory.getOrCreateChatModel(platform, apiKey.getApiKey(), apiKey.getUrl()),
                model);
    }

    private void validateKeyPlatform(Long keyId, String platform) {
        AiApiKeyDO apiKey = apiKeyService.validateApiKey(keyId);
        if (!apiKey.getPlatform().equals(platform)) {
            throw exception(MODEL_PLATFORM_MISMATCH);
        }
    }

    private void validatePlatformMatch(AiModelDO model, AiApiKeyDO apiKey) {
        if (!apiKey.getPlatform().equals(model.getPlatform())) {
            throw exception(MODEL_PLATFORM_MISMATCH);
        }
    }

}
