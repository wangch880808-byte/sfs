package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.controller.admin.model.vo.apikey.AiApiKeySaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.nus.zknu.module.ai.dal.mysql.model.AiApiKeyMapper;
import cn.nus.zknu.module.ai.dal.mysql.model.AiChatMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.API_KEY_IN_USE;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.API_KEY_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiApiKeyServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiApiKeyServiceImpl apiKeyService;
    @Mock
    private AiApiKeyMapper apiKeyMapper;
    @Mock
    private AiChatMapper modelMapper;

    @Test
    void createRejectsBlankSecret() {
        AiApiKeySaveReqVO request = request(null, "");
        assertServiceException(() -> apiKeyService.createApiKey(request), API_KEY_REQUIRED);
    }

    @Test
    void updateKeepsExistingSecretWhenBlank() {
        when(apiKeyMapper.selectById(1L)).thenReturn(key(1L, "old-secret"));
        AiApiKeySaveReqVO request = request(1L, "");

        apiKeyService.updateApiKey(request);

        ArgumentCaptor<AiApiKeyDO> captor = ArgumentCaptor.forClass(AiApiKeyDO.class);
        verify(apiKeyMapper).updateById(captor.capture());
        assertEquals("old-secret", captor.getValue().getApiKey());
    }

    @Test
    void deleteRejectsReferencedKey() {
        when(apiKeyMapper.selectById(1L)).thenReturn(key(1L, "secret"));
        when(modelMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertServiceException(() -> apiKeyService.deleteApiKey(1L), API_KEY_IN_USE);
    }

    @Test
    void plaintextMigrationRewritesOnlyExistingSecrets() {
        when(apiKeyMapper.selectPlaintextApiKeyIds()).thenReturn(List.of(1L, 2L));
        when(apiKeyMapper.selectById(1L)).thenReturn(key(1L, "secret"));
        when(apiKeyMapper.selectById(2L)).thenReturn(null);

        assertEquals(1, apiKeyService.migratePlaintextApiKeys());
        verify(apiKeyMapper).updateById(any(AiApiKeyDO.class));
    }

    private static AiApiKeySaveReqVO request(Long id, String secret) {
        AiApiKeySaveReqVO request = new AiApiKeySaveReqVO();
        request.setId(id);
        request.setName("primary");
        request.setPlatform("OpenAI");
        request.setApiKey(secret);
        request.setStatus(0);
        return request;
    }

    private static AiApiKeyDO key(Long id, String secret) {
        return AiApiKeyDO.builder().id(id).name("primary").platform("OpenAI")
                .apiKey(secret).status(0).build();
    }

}
