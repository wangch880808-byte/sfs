package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingRespVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingSaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiBusinessModelBindingDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.mysql.model.AiBusinessModelBindingMapper;
import cn.nus.zknu.module.ai.dal.mysql.model.AiChatMapper;
import cn.nus.zknu.module.ai.enums.model.AiBusinessCodeEnum;
import cn.nus.zknu.module.ai.enums.model.AiModelTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.BUSINESS_MODEL_INVALID;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.BUSINESS_MODEL_NOT_CONFIGURED;

@Service
public class AiBusinessModelBindingServiceImpl implements AiBusinessModelBindingService {

    @Resource
    private AiBusinessModelBindingMapper bindingMapper;
    @Resource
    private AiChatMapper modelMapper;
    @Resource
    private AiModelService modelService;

    @Override
    public List<AiBusinessModelBindingRespVO> getBindingList() {
        Map<String, AiBusinessModelBindingDO> bindingMap = bindingMapper.selectList().stream()
                .collect(Collectors.toMap(AiBusinessModelBindingDO::getBusinessCode, Function.identity()));
        Map<Long, AiModelDO> modelMap = modelMapper.selectList().stream()
                .collect(Collectors.toMap(AiModelDO::getId, Function.identity()));
        return java.util.Arrays.stream(AiBusinessCodeEnum.values()).map(item -> {
            AiBusinessModelBindingRespVO result = new AiBusinessModelBindingRespVO();
            result.setBusinessCode(item.getCode());
            result.setBusinessName(item.getName());
            AiBusinessModelBindingDO binding = bindingMap.get(item.getCode());
            if (binding == null) {
                result.setAvailable(false);
                return result;
            }
            result.setModelId(binding.getModelId());
            AiModelDO model = modelMap.get(binding.getModelId());
            result.setModelName(model != null ? model.getName() : null);
            result.setAvailable(isAvailableChatModel(model));
            return result;
        }).toList();
    }

    @Override
    public void saveBinding(AiBusinessModelBindingSaveReqVO request) {
        validateBinding(request);
        persistBinding(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBindings(List<AiBusinessModelBindingSaveReqVO> requests) {
        java.util.Set<String> codes = requests.stream()
                .map(AiBusinessModelBindingSaveReqVO::getBusinessCode).collect(Collectors.toSet());
        if (requests.size() != AiBusinessCodeEnum.values().length || codes.size() != requests.size()) {
            throw new IllegalArgumentException("请一次提交全部业务绑定，业务码不能重复");
        }
        requests.forEach(this::validateBinding);
        requests.forEach(this::persistBinding);
    }

    private void validateBinding(AiBusinessModelBindingSaveReqVO request) {
        AiBusinessCodeEnum.validate(request.getBusinessCode());
        AiModelDO model = modelService.validateModel(request.getModelId());
        if (!AiModelTypeEnum.CHAT.getType().equals(model.getType())) {
            throw exception(BUSINESS_MODEL_INVALID);
        }
    }

    private void persistBinding(AiBusinessModelBindingSaveReqVO request) {
        AiBusinessModelBindingDO existing = bindingMapper.selectByBusinessCode(request.getBusinessCode());
        if (existing == null) {
            AiBusinessModelBindingDO binding = new AiBusinessModelBindingDO();
            binding.setBusinessCode(request.getBusinessCode());
            binding.setModelId(request.getModelId());
            bindingMapper.insert(binding);
        } else {
            existing.setModelId(request.getModelId());
            bindingMapper.updateById(existing);
        }
    }

    @Override
    public AiModelDO getRequiredModel(String businessCode) {
        AiBusinessCodeEnum.validate(businessCode);
        AiBusinessModelBindingDO binding = bindingMapper.selectByBusinessCode(businessCode);
        if (binding == null) {
            throw exception(BUSINESS_MODEL_NOT_CONFIGURED, businessCode);
        }
        AiModelDO model = modelService.validateModel(binding.getModelId());
        if (!AiModelTypeEnum.CHAT.getType().equals(model.getType())) {
            throw exception(BUSINESS_MODEL_INVALID);
        }
        return model;
    }

    private boolean isAvailableChatModel(AiModelDO model) {
        return model != null && AiModelTypeEnum.CHAT.getType().equals(model.getType())
                && CommonStatusEnum.ENABLE.getStatus().equals(model.getStatus());
    }

}
