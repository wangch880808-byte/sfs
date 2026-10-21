package cn.nus.zknu.module.edu.service.aireview.config;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EduAiReviewPolicyTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduAiReviewPolicy policy;
    @Mock
    private ConfigApi configApi;
    @Mock
    private AiEduReviewApi aiEduReviewApi;
    @Spy
    private EduAiReviewProperties reviewProperties = new EduAiReviewProperties();

    @Test
    void readsConfiguredValues() {
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.DISPATCH_INTERVAL_SECONDS)).thenReturn("30");
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.DISPATCH_BATCH_SIZE)).thenReturn(" 50 ");
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.QUEUE_TIMEOUT_MINUTES)).thenReturn("60");
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.FAIL_NOTIFY_HOURS)).thenReturn("12");

        assertEquals(30, policy.dispatchIntervalSeconds());
        assertEquals(50, policy.dispatchBatchSize());
        assertEquals(60, policy.queueTimeoutMinutes());
        assertEquals(12, policy.failNotifyHours());
    }

    @Test
    void fallsBackToDefaultsWhenMissingInvalidOrOutOfRange() {
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.DISPATCH_INTERVAL_SECONDS)).thenReturn(null);
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.DISPATCH_BATCH_SIZE)).thenReturn("abc");
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.QUEUE_TIMEOUT_MINUTES)).thenReturn("1");
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.FAIL_NOTIFY_HOURS)).thenReturn("2.5");

        assertEquals(15, policy.dispatchIntervalSeconds());
        assertEquals(20, policy.dispatchBatchSize());
        assertEquals(120, policy.queueTimeoutMinutes());
        assertEquals(6, policy.failNotifyHours());
    }

    @Test
    void configReadErrorFallsBackToDefault() {
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.FAIL_NOTIFY_HOURS))
                .thenThrow(new IllegalStateException("db"));

        assertEquals(6, policy.failNotifyHours());
    }

    @Test
    void requeueOnStartupDefaultsToTrueAndAcceptsOnlyBooleans() {
        when(configApi.getConfigValueByKey(EduAiReviewPolicy.REQUEUE_ON_STARTUP))
                .thenReturn(null, "false", " FALSE ", "true", "no", "0");

        assertTrue(policy.requeueOnStartup());
        assertFalse(policy.requeueOnStartup());
        assertFalse(policy.requeueOnStartup());
        assertTrue(policy.requeueOnStartup());
        assertTrue(policy.requeueOnStartup(), "非法值回退默认 true");
        assertTrue(policy.requeueOnStartup(), "非法值回退默认 true");
    }

    @Test
    void processingTimeoutCoversModelRetriesAndAttachments() {
        // 硬超时经 ai 模块的 api 读取（nus.ai.edu.hard-timeout，默认 180 秒）
        when(aiEduReviewApi.getReviewHardTimeout()).thenReturn(java.time.Duration.ofSeconds(180));
        // 硬超时 180 秒、5 个附件：max(3, ceil((540 + 1500) / 180)) = 12 倍
        assertEquals(2160L, policy.processingTimeoutSeconds());
    }

    @Test
    void processingTimeoutSurvivesMissingHardTimeout() {
        when(aiEduReviewApi.getReviewHardTimeout()).thenReturn(null);
        // 按 1 秒计：max(3, 1 * 3 + 1500 + 0) 倍
        assertEquals(1503L, policy.processingTimeoutSeconds());
    }
}
