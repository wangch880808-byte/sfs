package cn.nus.zknu.module.ai.api.edu.support;

import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.ai.framework.ai.core.governance.AiCallContext;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.Semaphore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.EDU_AI_BUSY;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.EDU_AI_TIMEOUT;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.EDU_EXTERNAL_STUDENT_DATA_DISABLED;

@Component
@Slf4j
public class AiEduRuntimeGuard {

    private final NusAiProperties properties;
    private final Semaphore permits;
    private final ExecutorService callExecutor;

    public AiEduRuntimeGuard(NusAiProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(Math.max(1, properties.getEdu().getMaxConcurrent()), true);
        AtomicInteger threadNumber = new AtomicInteger();
        this.callExecutor = new ThreadPoolExecutor(0, Math.max(1, properties.getEdu().getMaxConcurrent()),
                60L, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
                    Thread thread = new Thread(task, "edu-ai-call-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    public void shutdown() {
        callExecutor.shutdownNow();
    }

    public void requireExternalStudentDataEnabled() {
        if (!properties.getEdu().isExternalStudentDataEnabled()) {
            throw exception(EDU_EXTERNAL_STUDENT_DATA_DISABLED);
        }
    }

    /**
     * 在并发和硬超时保护下执行一次教育 AI 调用。
     * 页面请求在并发已满时立即提示繁忙；后台任务（线程池、定时任务）最多排队等待 backgroundPermitWait。
     */
    public <T> T execute(String businessCode, AiModelDO model, String businessObjectId, Supplier<T> action) {
        boolean background = AiCallContext.isBackgroundThread();
        if (!acquire(background)) {
            throw exception(EDU_AI_BUSY);
        }
        long startedAt = System.nanoTime();
        Long operatorId = getLoginUserId();
        AiCallContext context = new AiCallContext(businessCode, businessObjectId, operatorId, background, true);
        tagActiveSpan(businessCode, model);
        try {
            Future<T> future;
            try {
                future = callExecutor.submit(() -> AiCallContext.call(context, action));
            } catch (RejectedExecutionException ex) {
                throw exception(EDU_AI_BUSY);
            }
            T result;
            try {
                result = future.get(Math.max(1L, properties.getEdu().getHardTimeout().toMillis()), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw exception(EDU_AI_TIMEOUT);
            } catch (InterruptedException ex) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw exception(EDU_AI_TIMEOUT);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(cause);
            }
            ActiveSpan.tag("ai.result", "success");
            audit("success", businessCode, model, businessObjectId, operatorId, startedAt, null);
            return result;
        } catch (RuntimeException ex) {
            ActiveSpan.tag("ai.result", "failed");
            ActiveSpan.error(ex);
            audit("failed", businessCode, model, businessObjectId, operatorId,
                    startedAt, ex.getClass().getSimpleName());
            throw ex;
        } finally {
            permits.release();
        }
    }

    public <T> Flux<T> executeStream(String businessCode, AiModelDO model, String businessObjectId,
                                     Supplier<Flux<T>> action) {
        // 是否后台调用按发起线程判断，订阅可能发生在其他线程
        boolean background = AiCallContext.isBackgroundThread();
        Long callerId = getLoginUserId();
        return Flux.defer(() -> {
            if (!acquire(background)) {
                return Flux.error(exception(EDU_AI_BUSY));
            }
            long startedAt = System.nanoTime();
            Long operatorId = callerId != null ? callerId : getLoginUserId();
            AiCallContext context = new AiCallContext(businessCode, businessObjectId, operatorId, background, true);
            tagActiveSpan(businessCode, model);
            try {
                return AiCallContext.call(context, action)
                        .timeout(properties.getEdu().getCallTimeout())
                        .doOnComplete(() -> {
                            ActiveSpan.tag("ai.result", "success");
                            audit("success", businessCode, model, businessObjectId, operatorId, startedAt, null);
                        })
                        .doOnError(ex -> {
                            ActiveSpan.tag("ai.result", "failed");
                            ActiveSpan.error(ex);
                            audit("failed", businessCode, model, businessObjectId,
                                    operatorId, startedAt, ex.getClass().getSimpleName());
                        })
                        .doFinally(signal -> permits.release());
            } catch (RuntimeException ex) {
                ActiveSpan.tag("ai.result", "failed");
                ActiveSpan.error(ex);
                audit("failed", businessCode, model, businessObjectId, operatorId,
                        startedAt, ex.getClass().getSimpleName());
                permits.release();
                return Flux.error(ex);
            }
        });
    }

    /** 页面请求不等待；后台任务排队等待，等待期间被中断视为繁忙。 */
    boolean acquire(boolean background) {
        if (!background) {
            return permits.tryAcquire();
        }
        long waitMillis = Math.max(0L, properties.getEdu().getBackgroundPermitWait().toMillis());
        try {
            return permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void tagActiveSpan(String businessCode, AiModelDO model) {
        ActiveSpan.tag("ai.business_code", businessCode);
        ActiveSpan.tag("ai.model_id", String.valueOf(model.getId()));
        ActiveSpan.tag("ai.platform", String.valueOf(model.getPlatform()));
    }

    private void audit(String result, String businessCode, AiModelDO model, String businessObjectId,
                       Long operatorId, long startedAt, String errorType) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        log.info("[eduAiAudit] businessCode={}, modelId={}, model={}, operatorId={}, businessObjectId={}, "
                        + "durationMs={}, result={}, errorType={}",
                businessCode, model.getId(), model.getModel(), operatorId, businessObjectId,
                durationMs, result, errorType);
    }

}
