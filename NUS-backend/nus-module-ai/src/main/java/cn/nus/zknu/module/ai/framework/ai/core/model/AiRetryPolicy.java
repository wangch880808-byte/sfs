package cn.nus.zknu.module.ai.framework.ai.core.model;

import cn.nus.zknu.module.ai.framework.ai.core.governance.AiErrorClassifier;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 有时间预算的重试策略：只对可重试错误重试，并且只有当“已用时间 + 下次等待 + 一次完整尝试”
 * 不超过总时限时才开始下一次尝试，避免重试越过调用方的硬超时。
 */
public class AiRetryPolicy implements RetryPolicy {

    private static final String START_NANOS = AiRetryPolicy.class.getName() + ".start";

    private final int maxAttempts;
    private final long budgetNanos;
    private final long attemptTimeoutNanos;
    private final long initialIntervalMillis;
    private final double multiplier;
    private final long maxIntervalMillis;
    private final LongSupplier nanoClock;

    public AiRetryPolicy(int maxAttempts, Duration budget, Duration attemptTimeout,
                         Duration initialInterval, double multiplier, Duration maxInterval,
                         LongSupplier nanoClock) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.budgetNanos = Math.max(0L, budget.toNanos());
        this.attemptTimeoutNanos = Math.max(0L, attemptTimeout.toNanos());
        this.initialIntervalMillis = Math.max(0L, initialInterval.toMillis());
        this.multiplier = Math.max(1.0, multiplier);
        this.maxIntervalMillis = Math.max(this.initialIntervalMillis, maxInterval.toMillis());
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean canRetry(RetryContext context) {
        Throwable last = context.getLastThrowable();
        if (last == null) {
            return true;
        }
        int attempts = context.getRetryCount();
        if (attempts >= maxAttempts || !AiErrorClassifier.isRetryable(last)) {
            return false;
        }
        Object start = context.getAttribute(START_NANOS);
        long elapsed = start instanceof Long startNanos ? nanoClock.getAsLong() - startNanos : 0L;
        long nextBackoffNanos = Duration.ofMillis(backOffMillis(attempts)).toNanos();
        return elapsed + nextBackoffNanos + attemptTimeoutNanos <= budgetNanos;
    }

    /** 第 n 次重试前的等待时间（n 从 1 开始），与 ExponentialBackOffPolicy 的计算一致。 */
    long backOffMillis(int retryNumber) {
        double interval = initialIntervalMillis * Math.pow(multiplier, Math.max(0, retryNumber - 1));
        return (long) Math.min(interval, maxIntervalMillis);
    }

    @Override
    public RetryContext open(RetryContext parent) {
        RetryContextSupport context = new RetryContextSupport(parent);
        context.setAttribute(START_NANOS, nanoClock.getAsLong());
        return context;
    }

    @Override
    public void close(RetryContext context) {
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        ((RetryContextSupport) context).registerThrowable(throwable);
    }

}
