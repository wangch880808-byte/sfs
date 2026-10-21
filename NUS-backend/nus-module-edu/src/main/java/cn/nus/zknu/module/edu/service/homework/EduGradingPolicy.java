package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 批改相关的可配置项，读取失败或超出范围时回退默认值。 */
@Service
@Slf4j
public class EduGradingPolicy {

    public static final String UNDO_SECONDS_KEY = "edu.grading.undo-seconds";
    static final int DEFAULT_UNDO_SECONDS = 10;
    static final int MAX_UNDO_SECONDS = 120;

    @Resource
    private ConfigApi configApi;

    /** 批改、打回后可以撤销的秒数；0 表示不提供撤销，通知尽快发出。 */
    public int undoSeconds() {
        String raw;
        try {
            raw = configApi.getConfigValueByKey(UNDO_SECONDS_KEY);
        } catch (Exception ex) {
            log.warn("读取批改撤销时长失败，使用默认值 {} 秒", DEFAULT_UNDO_SECONDS);
            return DEFAULT_UNDO_SECONDS;
        }
        if (raw == null || raw.isBlank()) {
            return DEFAULT_UNDO_SECONDS;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isFinite(value) && value >= 0 && value <= MAX_UNDO_SECONDS) {
                return (int) Math.round(value);
            }
        } catch (NumberFormatException ignored) {
            // 落到下面的回退
        }
        log.warn("批改撤销时长配置无效，回退至 {} 秒：{}", DEFAULT_UNDO_SECONDS, raw);
        return DEFAULT_UNDO_SECONDS;
    }
}
