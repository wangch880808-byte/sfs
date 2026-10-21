package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.dal.dataobject.log.AiCallLogDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.dal.mysql.log.AiCallLogMapper;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.ai.service.log.AiCallLogService;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.AI_CIRCUIT_OPEN;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.AI_QUOTA_EXCEEDED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 统一治理：调用日志、额度、熔断。用真实的熔断与额度组件，只模拟数据库、配置和底层模型。
 */
class AiGovernedChatModelTest extends BaseMockitoUnitTest {

    @Mock
    private ChatModel delegate;
    @Mock
    private AiCallLogService callLogService;
    @Mock
    private AiCallLogMapper callLogMapper;
    @Mock
    private ConfigApi configApi;

    private final AtomicLong nanos = new AtomicLong(5_000_000_000L);
    private final Map<String, String> configValues = new HashMap<>();
    private AiCallGovernor governor;
    private AiCircuitBreaker breaker;
    private final AiModelDO model = AiModelDO.builder().id(11L).model("deepseek-chat").platform("DeepSeek").build();
    private final Prompt prompt = new Prompt("hi");

    @BeforeEach
    void setUp() {
        lenient().when(configApi.getConfigValueByKey(anyString()))
                .thenAnswer(invocation -> configValues.get(invocation.<String>getArgument(0)));
        NusAiProperties properties = new NusAiProperties();
        properties.setConfigCacheTtl(Duration.ZERO);
        AiGovernanceConfig config = new AiGovernanceConfig();
        ReflectionTestUtils.setField(config, "configApi", configApi);
        ReflectionTestUtils.setField(config, "aiProperties", properties);
        breaker = new AiCircuitBreaker(config, nanos::get);
        AiQuotaChecker quota = new AiQuotaChecker();
        ReflectionTestUtils.setField(quota, "config", config);
        ReflectionTestUtils.setField(quota, "callLogMapper", callLogMapper);
        ReflectionTestUtils.setField(quota, "aiProperties", properties);
        governor = new AiCallGovernor();
        ReflectionTestUtils.setField(governor, "circuitBreaker", breaker);
        ReflectionTestUtils.setField(governor, "quotaChecker", quota);
        ReflectionTestUtils.setField(governor, "callLogService", callLogService);
    }

    private ChatModel governed() {
        return governor.govern(delegate, model);
    }

    @Test
    void successfulCallIsLoggedWithUsageAndContext() {
        when(delegate.call(prompt)).thenReturn(response("ok", new DefaultUsage(120, 30)));

        ChatResponse result = AiCallContext.call(new AiCallContext("HOMEWORK_REVIEW", "42", 7L, true, true),
                () -> governed().call(prompt));

        assertEquals("ok", result.getResult().getOutput().getText());
        AiCallLogDO log = captureSingleLog();
        assertEquals("HOMEWORK_REVIEW", log.getBusinessCode());
        assertEquals("42", log.getBizId());
        assertEquals(7L, log.getOperatorId());
        assertTrue(log.getBackground());
        assertEquals(11L, log.getModelId());
        assertEquals("deepseek-chat", log.getModelName());
        assertEquals(120, log.getPromptTokens());
        assertEquals(30, log.getCompletionTokens());
        assertEquals(150, log.getTotalTokens());
        assertTrue(log.getSuccess());
        assertNull(log.getErrorType());
        assertNotNull(log.getCreateTime());
    }

    @Test
    void missingUsageIsStoredAsNull() {
        when(delegate.call(prompt)).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));

        governed().call(prompt);

        AiCallLogDO log = captureSingleLog();
        assertNull(log.getPromptTokens());
        assertNull(log.getTotalTokens());
    }

    @Test
    void failedCallIsLoggedWithSanitizedSummaryAndRethrown() {
        when(delegate.call(prompt)).thenThrow(new TransientAiException(
                "503 - upstream down for https://api.example.com/v1?key=sk-abcdefghijklmnopqrstuvwxyz123456"));

        assertThrows(TransientAiException.class, () -> governed().call(prompt));

        AiCallLogDO log = captureSingleLog();
        assertFalse(log.getSuccess());
        assertEquals(AiErrorClassifier.TYPE_SERVER_ERROR, log.getErrorType());
        assertTrue(log.getErrorMessage().startsWith("SERVER_ERROR 503"), log.getErrorMessage());
        assertFalse(log.getErrorMessage().contains("api.example.com"), log.getErrorMessage());
        assertFalse(log.getErrorMessage().contains("sk-abc"), log.getErrorMessage());
        assertTrue(log.getErrorMessage().length() <= 200);
    }

    @Test
    void loggingFailureDoesNotBreakTheCall() {
        when(delegate.call(prompt)).thenReturn(response("ok", new DefaultUsage(1, 1)));
        doThrow(new IllegalStateException("db down")).when(callLogService).record(any());

        assertEquals("ok", governed().call(prompt).getResult().getOutput().getText());
    }

    @Test
    void streamIsLoggedOnceWithLastReportedUsage() {
        when(delegate.stream(prompt)).thenReturn(Flux.just(response("a", null), response("b", new DefaultUsage(10, 5))));

        List<ChatResponse> chunks = governed().stream(prompt).collectList().block();

        assertEquals(2, chunks.size());
        AiCallLogDO log = captureSingleLog();
        assertTrue(log.getSuccess());
        assertEquals(15, log.getTotalTokens());
    }

    @Test
    void dailyTokenQuotaRejectsBeforeCallingModel() {
        configValues.put(AiGovernanceConfig.DAILY_TOKEN_QUOTA, "1000");
        when(callLogMapper.selectTotalTokensSince(any())).thenReturn(1000L);

        assertServiceException(() -> governed().call(prompt), AI_QUOTA_EXCEEDED);

        verify(delegate, never()).call(any(Prompt.class));
        AiCallLogDO log = captureSingleLog();
        assertEquals(AiErrorClassifier.TYPE_QUOTA_EXCEEDED, log.getErrorType());
        assertEquals("今日 AI 用量已达上限，明天再试或联系管理员调整", log.getErrorMessage());
    }

    @Test
    void userDailyCallQuotaLimitsInteractiveCallsOnly() {
        configValues.put(AiGovernanceConfig.USER_DAILY_CALL_QUOTA, "3");
        when(callLogMapper.selectCallCountByOperatorSince(eq(7L), any())).thenReturn(3L);

        // 页面上由用户发起的调用：已用满 3 次，拒绝
        assertServiceException(() -> AiCallContext.call(new AiCallContext("CLASS_ANALYSIS", null, 7L, false, true),
                () -> governed().call(prompt)), AI_QUOTA_EXCEEDED);
        verify(delegate, never()).call(any(Prompt.class));
        verify(callLogMapper, times(1)).selectCallCountByOperatorSince(eq(7L), any());
    }

    @Test
    void backgroundCallsAreNeitherCountedNorBlockedByUserQuota() {
        configValues.put(AiGovernanceConfig.USER_DAILY_CALL_QUOTA, "1");
        when(delegate.call(prompt)).thenReturn(response("ok", new DefaultUsage(10, 5)));

        // 同一个发起人在后台连续调用多次（AI 初评队列、后台重新生成），都不查也不受每人次数限制
        for (int i = 0; i < 3; i++) {
            assertEquals("ok", AiCallContext.call(new AiCallContext("HOMEWORK_REVIEW", "r" + i, 7L, true, true),
                    () -> governed().call(prompt)).getResult().getOutput().getText());
        }

        verify(callLogMapper, never()).selectCallCountByOperatorSince(any(), any());
        ArgumentCaptor<AiCallLogDO> captor = ArgumentCaptor.forClass(AiCallLogDO.class);
        verify(callLogService, times(3)).record(captor.capture());
        // 日志仍记发起人，并标为后台调用，统计每人次数时据此排除
        captor.getAllValues().forEach(log -> {
            assertEquals(7L, log.getOperatorId());
            assertTrue(log.getBackground());
        });
    }

    @Test
    void backgroundCallsStillCountTowardDailyTokenQuota() {
        configValues.put(AiGovernanceConfig.DAILY_TOKEN_QUOTA, "1000");
        configValues.put(AiGovernanceConfig.USER_DAILY_CALL_QUOTA, "1");
        when(callLogMapper.selectTotalTokensSince(any())).thenReturn(1200L);

        assertServiceException(() -> AiCallContext.call(new AiCallContext("HOMEWORK_REVIEW", "9", 7L, true, true),
                () -> governed().call(prompt)), AI_QUOTA_EXCEEDED);

        verify(delegate, never()).call(any(Prompt.class));
        verify(callLogMapper, never()).selectCallCountByOperatorSince(any(), any());
        AiCallLogDO log = captureSingleLog();
        assertEquals(AiErrorClassifier.TYPE_QUOTA_EXCEEDED, log.getErrorType());
        assertTrue(log.getBackground());
    }

    @Test
    void interactiveCallWithoutOperatorSkipsUserQuota() {
        configValues.put(AiGovernanceConfig.USER_DAILY_CALL_QUOTA, "1");
        when(delegate.call(prompt)).thenReturn(response("ok", null));

        AiCallContext.call(new AiCallContext("CLASS_ANALYSIS", null, null, false, true),
                () -> governed().call(prompt));

        verify(callLogMapper, never()).selectCallCountByOperatorSince(any(), any());
    }

    @Test
    void userCallCountQueryExcludesBackgroundAndRejectedCalls() throws NoSuchMethodException {
        String sql = String.join(" ", AiCallLogMapper.class
                .getMethod("selectCallCountByOperatorSince", Long.class, java.time.LocalDateTime.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        assertTrue(sql.contains("background = 0"), sql);
        assertTrue(sql.contains("'QUOTA_EXCEEDED', 'CIRCUIT_OPEN'"), sql);
    }

    @Test
    void zeroQuotaMeansUnlimitedAndSkipsCounting() {
        configValues.put(AiGovernanceConfig.DAILY_TOKEN_QUOTA, "0");
        configValues.put(AiGovernanceConfig.USER_DAILY_CALL_QUOTA, "0");
        when(delegate.call(prompt)).thenReturn(response("ok", null));

        governed().call(prompt);

        verify(callLogMapper, never()).selectTotalTokensSince(any());
        verify(callLogMapper, never()).selectCallCountByOperatorSince(anyLong(), any());
    }

    @Test
    void breakerOpensAfterConsecutiveServiceFailuresAndRecovers() {
        configValues.put(AiGovernanceConfig.BREAKER_FAILURE_THRESHOLD, "3");
        configValues.put(AiGovernanceConfig.BREAKER_OPEN_SECONDS, "60");
        when(delegate.call(prompt)).thenThrow(new TransientAiException("502 - bad gateway"));

        for (int i = 0; i < 3; i++) {
            assertThrows(TransientAiException.class, () -> governed().call(prompt));
        }
        assertServiceException(() -> governed().call(prompt), AI_CIRCUIT_OPEN);
        verify(delegate, times(3)).call(prompt);

        // 到期后放行；放行后成功一次即恢复
        nanos.addAndGet(Duration.ofSeconds(61).toNanos());
        reset(delegate);
        when(delegate.call(prompt)).thenReturn(response("ok", null));
        assertEquals("ok", governed().call(prompt).getResult().getOutput().getText());
        assertFalse(breaker.isOpen("model:11"));
    }

    @Test
    void halfOpenFailureReopensImmediately() {
        configValues.put(AiGovernanceConfig.BREAKER_FAILURE_THRESHOLD, "2");
        configValues.put(AiGovernanceConfig.BREAKER_OPEN_SECONDS, "30");
        when(delegate.call(prompt)).thenThrow(new TransientAiException("500 - boom"));
        assertThrows(TransientAiException.class, () -> governed().call(prompt));
        assertThrows(TransientAiException.class, () -> governed().call(prompt));
        assertTrue(breaker.isOpen("model:11"));

        nanos.addAndGet(Duration.ofSeconds(31).toNanos());
        assertThrows(TransientAiException.class, () -> governed().call(prompt));
        assertTrue(breaker.isOpen("model:11"));
    }

    @Test
    void clientErrorsDoNotOpenBreaker() {
        configValues.put(AiGovernanceConfig.BREAKER_FAILURE_THRESHOLD, "2");
        when(delegate.call(prompt)).thenThrow(new NonTransientAiException("401 - invalid key"));

        for (int i = 0; i < 4; i++) {
            assertThrows(NonTransientAiException.class, () -> governed().call(prompt));
        }
        assertFalse(breaker.isOpen("model:11"));
    }

    @Test
    void ungovernedConnectionTestBypassesBreakerAndQuota() {
        configValues.put(AiGovernanceConfig.BREAKER_FAILURE_THRESHOLD, "1");
        configValues.put(AiGovernanceConfig.BREAKER_OPEN_SECONDS, "600");
        breaker.recordFailure("model:11");
        assertTrue(breaker.isOpen("model:11"));
        when(delegate.call(prompt)).thenReturn(response("OK", null));

        ChatResponse result = AiCallContext.call(new AiCallContext("MODEL_TEST", "11", null, false, false),
                () -> governed().call(prompt));

        assertEquals("OK", result.getResult().getOutput().getText());
        assertFalse(breaker.isOpen("model:11"));
    }

    @Test
    void invalidConfigFallsBackToDefaults() {
        configValues.put(AiGovernanceConfig.BREAKER_FAILURE_THRESHOLD, "abc");
        configValues.put(AiGovernanceConfig.BREAKER_OPEN_SECONDS, "-5");
        configValues.put(AiGovernanceConfig.CALL_LOG_RETENTION_DAYS, null);
        AiGovernanceConfig config = (AiGovernanceConfig) ReflectionTestUtils.getField(breaker, "config");

        assertEquals(5, config.breakerFailureThreshold());
        assertEquals(60, config.breakerOpenSeconds());
        assertEquals(180, config.callLogRetentionDays());
    }

    private AiCallLogDO captureSingleLog() {
        ArgumentCaptor<AiCallLogDO> captor = ArgumentCaptor.forClass(AiCallLogDO.class);
        verify(callLogService).record(captor.capture());
        return captor.getValue();
    }

    private static ChatResponse response(String text, DefaultUsage usage) {
        ChatResponseMetadata metadata = usage == null ? ChatResponseMetadata.builder().build()
                : ChatResponseMetadata.builder().usage(usage).build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }


}
