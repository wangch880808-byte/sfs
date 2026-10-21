package cn.nus.zknu.module.edu.service.aireview.config;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 初评排队与通知的运行参数。值来自 infra_config（edu.ai-review.*），缺失或越界时回退默认值。
 */
@Component
@Slf4j
public class EduAiReviewPolicy {

    public static final String DISPATCH_INTERVAL_SECONDS = "edu.ai-review.dispatch-interval-seconds";
    public static final String DISPATCH_BATCH_SIZE = "edu.ai-review.dispatch-batch-size";
    public static final String QUEUE_TIMEOUT_MINUTES = "edu.ai-review.queue-timeout-minutes";
    public static final String FAIL_NOTIFY_HOURS = "edu.ai-review.fail-notify-hours";
    public static final String REQUEUE_ON_STARTUP = "edu.ai-review.requeue-on-startup";

    static final int DEFAULT_DISPATCH_INTERVAL_SECONDS = 15;
    static final int DEFAULT_DISPATCH_BATCH_SIZE = 20;
    static final int DEFAULT_QUEUE_TIMEOUT_MINUTES = 120;
    static final int DEFAULT_FAIL_NOTIFY_HOURS = 6;
    static final boolean DEFAULT_REQUEUE_ON_STARTUP = true;

    @Resource
    private ConfigApi configApi;
    @Resource
    private AiEduReviewApi aiEduReviewApi;
    @Resource
    private EduAiReviewProperties reviewProperties;

    /** 派发器扫描排队记录的间隔（秒），5–600。 */
    public int dispatchIntervalSeconds() {
        return readInt(DISPATCH_INTERVAL_SECONDS, DEFAULT_DISPATCH_INTERVAL_SECONDS, 5, 600);
    }

    /** 每次扫描最多派发的排队记录数，1–200。 */
    public int dispatchBatchSize() {
        return readInt(DISPATCH_BATCH_SIZE, DEFAULT_DISPATCH_BATCH_SIZE, 1, 200);
    }

    /** 排队超过该分钟数仍未开始即判为失败（队列积压），5–1440。 */
    public int queueTimeoutMinutes() {
        return readInt(QUEUE_TIMEOUT_MINUTES, DEFAULT_QUEUE_TIMEOUT_MINUTES, 5, 1440);
    }

    /** 同一任务的 AI 初评失败通知最短间隔（小时），1–168。 */
    public int failNotifyHours() {
        return readInt(FAIL_NOTIFY_HOURS, DEFAULT_FAIL_NOTIFY_HOURS, 1, 168);
    }

    /**
     * 服务启动时是否把上次运行遗留的「处理中」初评放回排队、自动续跑。默认开启；
     * 多实例部署时应关闭（另一实例可能正在处理），遗留记录改由超时判为失败。
     */
    public boolean requeueOnStartup() {
        String raw;
        try {
            raw = configApi.getConfigValueByKey(REQUEUE_ON_STARTUP);
        } catch (Exception ex) {
            log.warn("读取 AI 初评配置失败，使用默认值：{}", REQUEUE_ON_STARTUP);
            return DEFAULT_REQUEUE_ON_STARTUP;
        }
        if (StrUtil.isBlank(raw)) {
            return DEFAULT_REQUEUE_ON_STARTUP;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        log.warn("AI 初评配置 {} 应为 true 或 false，回退至 {}：{}", REQUEUE_ON_STARTUP,
                DEFAULT_REQUEUE_ON_STARTUP, raw);
        return DEFAULT_REQUEUE_ON_STARTUP;
    }

    /**
     * 单份初评处理中的最长时间（秒）：模型硬超时的 3 倍，再加上附件抽取（每个附件最多 5 分钟）。
     * 超过即视为超时或服务重启，由派发器判为失败。
     */
    public long processingTimeoutSeconds() {
        Duration hardTimeout = aiEduReviewApi.getReviewHardTimeout();
        long timeoutSeconds = Math.max(1, hardTimeout == null ? 0 : hardTimeout.toSeconds());
        long evidenceSeconds = Math.max(1, reviewProperties.getMaxAttachmentCount()) * 300L;
        long multiplier = Math.max(3L, (timeoutSeconds * 3L + evidenceSeconds + timeoutSeconds - 1) / timeoutSeconds);
        return timeoutSeconds * multiplier;
    }

    private int readInt(String key, int fallback, int min, int max) {
        String raw;
        try {
            raw = configApi.getConfigValueByKey(key);
        } catch (Exception ex) {
            log.warn("读取 AI 初评配置失败，使用默认值：{}", key);
            return fallback;
        }
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max) {
                log.warn("AI 初评配置 {} 超出范围 [{}, {}]，回退至 {}：{}", key, min, max, fallback, raw);
                return fallback;
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            log.warn("AI 初评配置 {} 不是数字，回退至 {}：{}", key, fallback, raw);
            return fallback;
        }
    }
}
