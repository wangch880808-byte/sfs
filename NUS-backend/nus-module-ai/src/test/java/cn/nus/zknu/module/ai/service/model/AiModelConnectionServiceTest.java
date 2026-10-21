package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiModelConnectionServiceTest {
    private final AiModelService models = mock(AiModelService.class);
    private final AiModelConnectionService service = new AiModelConnectionService(models);

    @Test
    void testsSavedChatModelAndCapsOutputWithoutStoringMessages() {
        var model = AiModelDO.builder().id(7L).type(1).platform("OpenAI")
                .model("custom-model").temperature(0.4).maxTokens(8192).build();
        when(models.validateModel(7L)).thenReturn(model);
        ChatModel client = mock(ChatModel.class);
        when(models.getChatModel(7L)).thenReturn(client);
        when(client.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("OK")))));
        assertTrue(service.testConnection(7L).isSuccess());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(client).call(prompt.capture());
        assertEquals("custom-model", prompt.getValue().getOptions().getModel());
        assertEquals(512, prompt.getValue().getOptions().getMaxTokens());
    }

    @Test
    void emptyResponseIsNotReportedAsSuccess() {
        var model = AiModelDO.builder().id(7L).type(1).platform("OpenAI").model("custom").build();
        when(models.validateModel(7L)).thenReturn(model);
        ChatModel client = mock(ChatModel.class);
        when(models.getChatModel(7L)).thenReturn(client);
        assertFalse(service.testConnection(7L).isSuccess());
    }

    @Test
    void providerErrorsNeverExposeSecrets() {
        var error = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "secret-api-key");
        assertEquals("认证或权限失败，请检查 API 密钥和模型使用权限",
                AiModelConnectionService.failureMessage(error));
        assertFalse(AiModelConnectionService.failureMessage(new RuntimeException("secret-api-key"))
                .contains("secret-api-key"));
        assertTrue(AiModelConnectionService.failureMessage(new RuntimeException(
                new java.util.concurrent.TimeoutException())).contains("超时"));
    }

    @Test
    void unsupportedTypeDoesNotSendProviderRequests() {
        when(models.validateModel(7L)).thenReturn(AiModelDO.builder().id(7L).type(3).build());
        assertFalse(service.testConnection(7L).isSuccess());
        verify(models, never()).getChatModel(anyLong());
    }
}
