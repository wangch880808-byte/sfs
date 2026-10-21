package cn.nus.zknu.module.ai.framework.ai.core.model;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiModelRetryTest extends BaseMockitoUnitTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    /** 退避缩短到毫秒级，时间预算用可控时钟。 */
    private RetryTemplate template(Duration callTimeout, Duration hardTimeout) {
        NusAiProperties.Edu edu = new NusAiProperties().getEdu();
        edu.setCallTimeout(callTimeout);
        edu.setHardTimeout(hardTimeout);
        edu.setRetryInitialInterval(Duration.ofMillis(1));
        edu.setRetryMaxInterval(Duration.ofMillis(2));
        return AiModelFactoryImpl.createRetryTemplate(edu, nanos::get);
    }

    private RetryTemplate defaults() {
        return template(Duration.ofSeconds(120), Duration.ofSeconds(180));
    }

    private int countAttempts(RetryTemplate template, RuntimeException error) {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(error.getClass(), () -> template.execute(context -> {
            calls.incrementAndGet();
            throw error;
        }));
        return calls.get();
    }

    @Test
    void quickTransientFailuresGetTwoRetries() {
        assertEquals(3, countAttempts(defaults(), new ResourceAccessException("temporary disconnect")));
        assertEquals(3, countAttempts(defaults(), new TransientAiException("503 - upstream unavailable")));
        assertEquals(3, countAttempts(defaults(), new NonTransientAiException("429 - rate limited")));
        assertEquals(3, countAttempts(defaults(), new HttpServerErrorException(HttpStatus.BAD_GATEWAY)));
        assertEquals(3, countAttempts(defaults(),
                new ResourceAccessException("read timed out", new SocketTimeoutException("timeout"))));
    }

    @Test
    void clientErrorsAreNotRetried() {
        assertEquals(1, countAttempts(defaults(), new NonTransientAiException("401 - invalid api key")));
        assertEquals(1, countAttempts(defaults(), new HttpClientErrorException(HttpStatus.BAD_REQUEST)));
        assertEquals(1, countAttempts(defaults(), new IllegalArgumentException("bad prompt")));
    }

    @Test
    void attemptThatTimedOutIsNotRetriedWhenAnotherFullAttemptWouldPassHardTimeout() {
        // 第一次尝试耗尽 120 秒读超时：再来一次最多还要 120 秒，会超过 180 秒硬超时，不再重试
        RetryTemplate template = defaults();
        AtomicInteger calls = new AtomicInteger();
        assertThrows(ResourceAccessException.class, () -> template.execute(context -> {
            calls.incrementAndGet();
            nanos.addAndGet(Duration.ofSeconds(120).toNanos());
            throw new ResourceAccessException("read timed out", new SocketTimeoutException("timeout"));
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void retriesStopOnceRemainingBudgetCannotFitAnAttempt() {
        // 每次 25 秒后失败，单次尝试上限 30 秒、总时限 90 秒：25+30 ≤ 90 可重试，50+30 ≤ 90 可重试，第 3 次后到上限
        RetryTemplate template = template(Duration.ofSeconds(30), Duration.ofSeconds(90));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(TransientAiException.class, () -> template.execute(context -> {
            calls.incrementAndGet();
            nanos.addAndGet(Duration.ofSeconds(25).toNanos());
            throw new TransientAiException("500 - boom");
        }));
        assertEquals(3, calls.get());

        // 每次 40 秒：40+30 ≤ 90 可重试一次，80+30 > 90 停止
        AtomicInteger slowCalls = new AtomicInteger();
        assertThrows(TransientAiException.class, () -> template.execute(context -> {
            slowCalls.incrementAndGet();
            nanos.addAndGet(Duration.ofSeconds(40).toNanos());
            throw new TransientAiException("500 - boom");
        }));
        assertEquals(2, slowCalls.get());
    }

    @Test
    void maxAttemptsIsConfigurable() {
        NusAiProperties.Edu edu = new NusAiProperties().getEdu();
        edu.setMaxAttempts(1);
        edu.setRetryInitialInterval(Duration.ofMillis(1));
        RetryTemplate template = AiModelFactoryImpl.createRetryTemplate(edu, nanos::get);
        assertEquals(1, countAttempts(template, new TransientAiException("503 - down")));
    }

    @Test
    void backoffGrowsAndIsCapped() {
        AiRetryPolicy policy = new AiRetryPolicy(5, Duration.ofSeconds(180), Duration.ofSeconds(120),
                Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4), nanos::get);
        assertEquals(1000L, policy.backOffMillis(1));
        assertEquals(2000L, policy.backOffMillis(2));
        assertEquals(4000L, policy.backOffMillis(3));
        assertEquals(4000L, policy.backOffMillis(4));
    }
}
