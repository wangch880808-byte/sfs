package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * AI 接入治理的参数，读自参数配置（infra_config），带默认值和范围校验；
 * 读到的值在本实例缓存 nus.ai.config-cache-ttl（默认 30 秒）。
 */
@Component
@Slf4j
public class AiGovernanceConfig {

    public static final String DAILY_TOKEN_QUOTA = "ai.daily-token-quota";
    public static final String USER_DAILY_CALL_QUOTA = "ai.user-daily-call-quota";
    public static final String BREAKER_FAILURE_THRESHOLD = "ai.breaker.failure-threshold";
    public static final String BREAKER_OPEN_SECONDS = "ai.breaker.open-seconds";
    public static final String CALL_LOG_RETENTION_DAYS = "ai.call-log.retention-days";

    @Resource
    private ConfigApi configApi;
    @Resource
    private NusAiProperties aiProperties;

    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();
    private LongSupplier nanoClock = System::nanoTime;

    /** 全平台每日令牌上限，0 表示不限。 */
    public long dailyTokenQuota() {
        return readLong(DAILY_TOKEN_QUOTA, 0, 0, 1_000_000_000_000L);
    }

    /** 每人每日模型调用次数上限，0 表示不限。 */
    public long userDailyCallQuota() {
        return readLong(USER_DAILY_CALL_QUOTA, 0, 0, 1_000_000L);
    }

    public int breakerFailureThreshold() {
        return (int) readLong(BREAKER_FAILURE_THRESHOLD, 5, 1, 1000);
    }

    public int breakerOpenSeconds() {
        return (int) readLong(BREAKER_OPEN_SECONDS, 60, 1, 86_400);
    }

    public int callLogRetentionDays() {
        return (int) readLong(CALL_LOG_RETENTION_DAYS, 180, 1, 3650);
    }

    private long readLong(String key, long fallback, long min, long max) {
        long now = nanoClock.getAsLong();
        CachedValue cached = cache.get(key);
        if (cached != null && now - cached.loadedAt() < aiProperties.getConfigCacheTtl().toNanos()) {
            return cached.value();
        }
        long value = parse(key, fallback, min, max);
        cache.put(key, new CachedValue(value, now));
        return value;
    }

    private long parse(String key, long fallback, long min, long max) {
        String raw;
        try {
            raw = configApi.getConfigValueByKey(key);
        } catch (Exception ex) {
            log.warn("[parse][读取 AI 配置失败，使用默认值] key={}", key);
            return fallback;
        }
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < min || value > max) {
                log.warn("[parse][AI 配置超出范围，使用默认值] key={}, value={}, range=[{}, {}]", key, raw, min, max);
                return fallback;
            }
            return value;
        } catch (NumberFormatException ex) {
            log.warn("[parse][AI 配置不是整数，使用默认值] key={}, value={}", key, raw);
            return fallback;
        }
    }

    @VisibleForTesting
    public void setNanoClock(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    @VisibleForTesting
    public void clearCache() {
        cache.clear();
    }

    private record CachedValue(long value, long loadedAt) {
    }

}
