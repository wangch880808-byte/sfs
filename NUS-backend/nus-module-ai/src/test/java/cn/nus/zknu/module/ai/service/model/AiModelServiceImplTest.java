package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.controller.admin.model.vo.model.AiModelSaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiBusinessModelBindingDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.mysql.model.AiBusinessModelBindingMapper;
import cn.nus.zknu.module.ai.dal.mysql.model.AiChatMapper;
import cn.nus.zknu.module.ai.framework.ai.core.model.AiModelFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.MODEL_PLATFORM_MISMATCH;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.MODEL_IN_USE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiModelServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiModelServiceImpl modelService;
    @Mock
    private AiApiKeyService apiKeyService;
    @Mock
    private AiChatMapper modelMapper;
    @Mock
    private AiModelFactory modelFactory;
    @Mock
    private AiBusinessModelBindingMapper bindingMapper;

    @Test
    void createRejectsKeyFromAnotherPlatform() {
        AiApiKeyDO key = AiApiKeyDO.builder().id(1L).platform("DeepSeek").status(0).build();
        when(apiKeyService.validateApiKey(1L)).thenReturn(key);
        AiModelSaveReqVO request = new AiModelSaveReqVO();
        request.setKeyId(1L);
        request.setPlatform("OpenAI");

        assertServiceException(() -> modelService.createModel(request), MODEL_PLATFORM_MISMATCH);
    }

    @Test
    void deleteRejectsBusinessBoundModel() {
        when(modelMapper.selectById(1L)).thenReturn(AiModelDO.builder().id(1L).build());
        when(bindingMapper.selectCount(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq(1L)))
                .thenReturn(1L);

        assertServiceException(() -> modelService.deleteModel(1L), MODEL_IN_USE);
    }

}
