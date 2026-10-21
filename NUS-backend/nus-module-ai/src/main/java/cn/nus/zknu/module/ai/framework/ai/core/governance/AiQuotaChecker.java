package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.nus.zknu.module.ai.dal.mysql.log.AiCallLogMapper;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.ai.enums.ErrorCodeConstants.AI_QUOTA_EXCEEDED;

/**
 * 每日用量额度，0 表示不限：
 * <ul>
 *     <li>ai.daily-token-quota：全平台令牌上限，页面调用和后台调用都计入、都受限；</li>
 *     <li>ai.user-daily-call-quota：每人调用次数上限，只统计和限制页面上由用户发起的调用；
 *     后台调用（AI 初评队列、后台重新生成、批量任务）既不计数也不受这项限制。</li>
 * </ul>
 * 用量来自调用日志；日志异步写入，并发调用时可能略超额度。统计失败时放行，不因统计故障中断教学。
 */
@Component
@Slf4j
public class AiQuotaChecker {

    @Resource
    private AiGovernanceConfig config;
    @Resource
    private AiCallLogMapper callLogMapper;
    @Resource
    private NusAiProperties aiProperties;

    private Clock clock = Clock.systemDefaultZone();
    private LongSupplier nanoClock = System::nanoTime;
    private volatile CachedTokens cachedTokens;

    /**
     * @param operatorId 发起人，可为空
     * @param background 是否后台调用；后台调用只检查全平台令牌额度
     */
    public void check(Long operatorId, boolean background) {
        long tokenQuota = config.dailyTokenQuota();
        if (tokenQuota > 0 && todayTokens() >= tokenQuota) {
            throw exception(AI_QUOTA_EXCEEDED);
        }
        if (background || operatorId == null) {
            return;
        }
        long callQuota = config.userDailyCallQuota();
        if (callQuota > 0 && todayCalls(operatorId) >= callQuota) {
            throw exception(AI_QUOTA_EXCEEDED);
        }
    }

    private long todayTokens() {
        LocalDate today = LocalDate.now(clock);
        long now = nanoClock.getAsLong();
        CachedTokens cached = cachedTokens;
        if (cached != null && cached.day().equals(today)
                && now - cached.loadedAt() < aiProperties.getConfigCacheTtl().toNanos()) {
            return cached.tokens();
        }
        try {
            Long tokens = callLogMapper.selectTotalTokensSince(today.atStartOfDay());
            long value = tokens != null ? tokens : 0L;
            cachedTokens = new CachedTokens(today, value, now);
            return value;
        } catch (Exception ex) {
            log.warn("[todayTokens][统计今日令牌失败，暂不限制]", ex);
            return 0L;
        }
    }

    private long todayCalls(Long operatorId) {
        LocalDateTime since = LocalDate.now(clock).atStartOfDay();
        try {
            Long count = callLogMapper.selectCallCountByOperatorSince(operatorId, since);
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.warn("[todayCalls][统计今日调用次数失败，暂不限制] operatorId={}", operatorId, ex);
            return 0L;
        }
    }

    @VisibleForTesting
    void setClocks(Clock clock, LongSupplier nanoClock) {
        this.clock = clock;
        this.nanoClock = nanoClock;
        this.cachedTokens = null;
    }

    private record CachedTokens(LocalDate day, long tokens, long loadedAt) {
    }

}
