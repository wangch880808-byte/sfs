package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingSaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiBusinessModelBindingDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.mysql.model.AiBusinessModelBindingMapper;
import cn.nus.zknu.module.ai.dal.mysql.model.AiChatMapper;
import cn.nus.zknu.module.ai.enums.model.AiBusinessCodeEnum;
import cn.nus.zknu.module.ai.enums.model.AiModelTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.BUSINESS_MODEL_INVALID;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.BUSINESS_MODEL_NOT_CONFIGURED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiBusinessModelBindingServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiBusinessModelBindingServiceImpl bindingService;
    @Mock
    private AiBusinessModelBindingMapper bindingMapper;
    @Mock
    private AiChatMapper modelMapper;
    @Mock
    private AiModelService modelService;

    @Test
    void getRequiredModelRejectsMissingBindingWithoutDefaultFallback() {
        String businessCode = AiBusinessCodeEnum.HOMEWORK_REVIEW.getCode();
        when(bindingMapper.selectByBusinessCode(businessCode)).thenReturn(null);

        assertServiceException(() -> bindingService.getRequiredModel(businessCode),
                BUSINESS_MODEL_NOT_CONFIGURED, businessCode);
        verify(modelMapper, never()).selectFirstByStatus(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getRequiredModelRejectsNonChatModel() {
        String businessCode = AiBusinessCodeEnum.HOMEWORK_REVIEW.getCode();
        when(bindingMapper.selectByBusinessCode(businessCode)).thenReturn(binding(1L, businessCode, 2L));
        when(modelService.validateModel(2L)).thenReturn(model(2L, 2));

        assertServiceException(() -> bindingService.getRequiredModel(businessCode), BUSINESS_MODEL_INVALID);
    }

    @Test
    void saveBindingInsertsNewBinding() {
        String businessCode = AiBusinessCodeEnum.HOMEWORK_REVIEW.getCode();
        when(modelService.validateModel(2L)).thenReturn(model(2L, AiModelTypeEnum.CHAT.getType()));
        when(bindingMapper.selectByBusinessCode(businessCode)).thenReturn(null);

        bindingService.saveBinding(request(businessCode, 2L));

        ArgumentCaptor<AiBusinessModelBindingDO> captor = ArgumentCaptor.forClass(AiBusinessModelBindingDO.class);
        verify(bindingMapper).insert(captor.capture());
        assertEquals(businessCode, captor.getValue().getBusinessCode());
        assertEquals(2L, captor.getValue().getModelId());
    }

    @Test
    void saveBindingUpdatesExistingBinding() {
        String businessCode = AiBusinessCodeEnum.HOMEWORK_REVIEW.getCode();
        AiBusinessModelBindingDO existing = binding(1L, businessCode, 2L);
        when(modelService.validateModel(3L)).thenReturn(model(3L, AiModelTypeEnum.CHAT.getType()));
        when(bindingMapper.selectByBusinessCode(businessCode)).thenReturn(existing);

        bindingService.saveBinding(request(businessCode, 3L));

        verify(bindingMapper).updateById(existing);
        assertEquals(3L, existing.getModelId());
    }

    @Test
    void batchValidatesEveryModelBeforeWriting() {
        var requests = java.util.Arrays.stream(AiBusinessCodeEnum.values())
                .map(code -> request(code.getCode(), 2L)).toList();
        requests.get(requests.size() - 1).setModelId(3L);
        when(modelService.validateModel(2L)).thenReturn(model(2L, AiModelTypeEnum.CHAT.getType()));
        when(modelService.validateModel(3L)).thenReturn(model(3L, 2));

        assertServiceException(() -> bindingService.saveBindings(requests), BUSINESS_MODEL_INVALID);
        org.mockito.Mockito.verifyNoInteractions(bindingMapper);
    }

    @Test
    void batchRejectsDuplicateAndIncompleteBusinessCodes() {
        var duplicate = java.util.Collections.nCopies(AiBusinessCodeEnum.values().length,
                request(AiBusinessCodeEnum.EDU_CHAT.getCode(), 2L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> bindingService.saveBindings(duplicate));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> bindingService.saveBindings(java.util.List.of(duplicate.get(0))));
        org.mockito.Mockito.verifyNoInteractions(bindingMapper);
    }

    @Test
    void batchSavesAllBusinessSelections() {
        var requests = java.util.Arrays.stream(AiBusinessCodeEnum.values())
                .map(code -> request(code.getCode(), 2L)).toList();
        when(modelService.validateModel(2L)).thenReturn(model(2L, AiModelTypeEnum.CHAT.getType()));
        bindingService.saveBindings(requests);
        ArgumentCaptor<AiBusinessModelBindingDO> captor = ArgumentCaptor.forClass(AiBusinessModelBindingDO.class);
        verify(bindingMapper, org.mockito.Mockito.times(requests.size())).insert(captor.capture());
        assertEquals(requests.stream().map(AiBusinessModelBindingSaveReqVO::getBusinessCode).toList(),
                captor.getAllValues().stream().map(AiBusinessModelBindingDO::getBusinessCode).toList());
    }

    private static AiBusinessModelBindingSaveReqVO request(String businessCode, Long modelId) {
        AiBusinessModelBindingSaveReqVO request = new AiBusinessModelBindingSaveReqVO();
        request.setBusinessCode(businessCode);
        request.setModelId(modelId);
        return request;
    }

    private static AiBusinessModelBindingDO binding(Long id, String businessCode, Long modelId) {
        AiBusinessModelBindingDO binding = new AiBusinessModelBindingDO();
        binding.setId(id);
        binding.setBusinessCode(businessCode);
        binding.setModelId(modelId);
        return binding;
    }

    private static AiModelDO model(Long id, Integer type) {
        return AiModelDO.builder().id(id).name("model").type(type).status(0).build();
    }

}
