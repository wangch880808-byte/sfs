package cn.nus.zknu.module.ai.api.edu.support;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.ai.framework.ai.core.governance.AiCallContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.EDU_EXTERNAL_STUDENT_DATA_DISABLED;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.EDU_AI_BUSY;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.EDU_AI_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEduRuntimeGuardTest extends BaseMockitoUnitTest {

    @Test
    void externalStudentDataIsDisabledByDefault() {
        AiEduRuntimeGuard guard = new AiEduRuntimeGuard(new NusAiProperties());

        assertServiceException(guard::requireExternalStudentDataEnabled, EDU_EXTERNAL_STUDENT_DATA_DISABLED);
    }

    @Test
    void failedCallReleasesConcurrencyPermit() {
        NusAiProperties properties = new NusAiProperties();
        properties.getEdu().setMaxConcurrent(1);
        AiEduRuntimeGuard guard = new AiEduRuntimeGuard(properties);
        AiModelDO model = AiModelDO.builder().id(1L).model("chat-model").build();

        assertThrows(IllegalStateException.class,
                () -> guard.execute("HOMEWORK_REVIEW", model, "1", () -> {
                    throw new IllegalStateException("failed");
                }));
        assertEquals("ok", guard.execute("HOMEWORK_REVIEW", model, "1", () -> "ok"));
    }

    @Test
    void hardTimeoutCancelsCallAndReleasesPermit() {
        NusAiProperties properties = new NusAiProperties();
        properties.getEdu().setMaxConcurrent(1);
        properties.getEdu().setHardTimeout(Duration.ofMillis(20));
        AiEduRuntimeGuard guard = new AiEduRuntimeGuard(properties);
        AiModelDO model = AiModelDO.builder().id(1L).model("chat-model").build();
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        assertServiceException(() -> guard.execute("HOMEWORK_REVIEW", model, "1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                stopped.countDown();
            }
            return "late";
        }), EDU_AI_TIMEOUT);
        try {
            org.junit.jupiter.api.Assertions.assertTrue(stopped.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
        org.junit.jupiter.api.Assertions.assertTrue(interrupted.get());
        assertEquals("ok", guard.execute("HOMEWORK_REVIEW", model, "1", () -> "ok"));
    }

    @Test
    void interactiveRequestFailsFastWhenAllPermitsAreBusy() throws Exception {
        NusAiProperties properties = new NusAiProperties();
        properties.getEdu().setMaxConcurrent(1);
        AiEduRuntimeGuard guard = new AiEduRuntimeGuard(properties);
        AiModelDO model = AiModelDO.builder().id(1L).model("chat-model").build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = holdPermit(guard, model, started, release);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            long begin = System.nanoTime();
            assertServiceException(() -> guard.execute("CLASS_ANALYSIS", model, "1", () -> "late"), EDU_AI_BUSY);
            assertTrue(System.nanoTime() - begin < TimeUnit.MILLISECONDS.toNanos(500));
        } finally {
            RequestContextHolder.resetRequestAttributes();
            release.countDown();
            holder.join(2000);
        }
    }

    @Test
    void backgroundCallWaitsForPermitAndSeesCallContext() throws Exception {
        NusAiProperties properties = new NusAiProperties();
        properties.getEdu().setMaxConcurrent(1);
        properties.getEdu().setBackgroundPermitWait(Duration.ofSeconds(5));
        AiEduRuntimeGuard guard = new AiEduRuntimeGuard(properties);
        AiModelDO model = AiModelDO.builder().id(1L).model("chat-model").build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = holdPermit(guard, model, started, release);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        new Thread(() -> {
            sleepQuietly(150);
            release.countDown();
        }).start();

        AiCallContext seen = guard.execute("HOMEWORK_REVIEW", model, "88", AiCallContext::current);

        assertEquals("HOMEWORK_REVIEW", seen.businessCode());
        assertEquals("88", seen.bizId());
        assertTrue(seen.background());
        holder.join(2000);
    }

    @Test
    void backgroundCallGivesUpAfterConfiguredWait() throws Exception {
        NusAiProperties properties = new NusAiProperties();
        properties.getEdu().setMaxConcurrent(1);
        properties.getEdu().setBackgroundPermitWait(Duration.ofMillis(100));
        AiEduRuntimeGuard guard = new AiEduRuntimeGuard(properties);
        AiModelDO model = AiModelDO.builder().id(1L).model("chat-model").build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = holdPermit(guard, model, started, release);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        try {
            long begin = System.nanoTime();
            assertServiceException(() -> guard.execute("HOMEWORK_REVIEW", model, "1", () -> "late"), EDU_AI_BUSY);
            assertTrue(System.nanoTime() - begin >= TimeUnit.MILLISECONDS.toNanos(90));
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    private static Thread holdPermit(AiEduRuntimeGuard guard, AiModelDO model,
                                     CountDownLatch started, CountDownLatch release) {
        Thread thread = new Thread(() -> guard.execute("HOMEWORK_REVIEW", model, "0", () -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "held";
        }));
        thread.start();
        return thread;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
