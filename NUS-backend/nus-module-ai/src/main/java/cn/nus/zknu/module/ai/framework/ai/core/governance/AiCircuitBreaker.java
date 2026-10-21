package cn.nus.zknu.module.ai.framework.ai.core.governance;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.AI_CIRCUIT_OPEN;

/**
 * 按模型计的简单熔断：连续 N 次服务侧失败（超时、限流、5xx、连接失败）后，在 M 秒内直接拒绝调用。
 * 到期后放行调用；放行后再失败一次立即重新熔断，成功一次则清零。
 */
@Component
@Slf4j
public class AiCircuitBreaker {

    @Resource
    private AiGovernanceConfig config;

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private LongSupplier nanoClock = System::nanoTime;

    public AiCircuitBreaker() {
    }

    @VisibleForTesting
    public AiCircuitBreaker(AiGovernanceConfig config, LongSupplier nanoClock) {
        this.config = config;
        this.nanoClock = nanoClock;
    }

    /** 熔断中抛出“AI 服务暂时不可用”。 */
    public void checkAllowed(String key) {
        State state = states.get(key);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.openUntil != 0L && nanoClock.getAsLong() - state.openUntil < 0) {
                throw exception(AI_CIRCUIT_OPEN);
            }
        }
    }

    public void recordSuccess(String key) {
        State state = states.get(key);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.consecutiveFailures > 0 || state.openUntil != 0L) {
                log.info("[recordSuccess][AI 模型恢复] key={}", key);
            }
            state.consecutiveFailures = 0;
            state.openUntil = 0L;
        }
    }

    public void recordFailure(String key) {
        State state = states.computeIfAbsent(key, ignored -> new State());
        int threshold = config.breakerFailureThreshold();
        synchronized (state) {
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= threshold) {
                long openNanos = config.breakerOpenSeconds() * 1_000_000_000L;
                state.openUntil = nanoClock.getAsLong() + openNanos;
                // 避免 0 被当作“未熔断”
                if (state.openUntil == 0L) {
                    state.openUntil = 1L;
                }
                log.warn("[recordFailure][AI 模型连续失败 {} 次，暂停调用 {} 秒] key={}",
                        state.consecutiveFailures, config.breakerOpenSeconds(), key);
            }
        }
    }

    @VisibleForTesting
    public boolean isOpen(String key) {
        try {
            checkAllowed(key);
            return false;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static final class State {
        private int consecutiveFailures;
        private long openUntil;
    }

}
