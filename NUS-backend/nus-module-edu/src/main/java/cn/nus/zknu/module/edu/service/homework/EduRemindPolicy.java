package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 截止提醒、教师催交与新提交汇总的配置入口。配置缺失或越界时回退到默认值并记录日志。
 */
@Service
@Slf4j
public class EduRemindPolicy {

    static final String KEY_REMIND_ENABLED = "edu.remind.enabled";
    static final String KEY_REMIND_BEFORE_HOURS = "edu.remind.before-hours";
    static final String KEY_MANUAL_INTERVAL_MINUTES = "edu.remind.manual-interval-minutes";
    static final String KEY_TEACHER_DIGEST_MINUTES = "edu.notify.teacher-digest-minutes";

    static final int DEFAULT_BEFORE_HOURS = 24;
    static final int DEFAULT_MANUAL_INTERVAL_MINUTES = 60;
    static final int DEFAULT_TEACHER_DIGEST_MINUTES = 60;

    @Resource
    private ConfigApi configApi;

    /** 是否在截止前自动提醒未提交的学生，默认开启 */
    public boolean remindEnabled() {
        String value = readValue(KEY_REMIND_ENABLED);
        if (StrUtil.isBlank(value)) {
            return true;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return "true".equals(normalized);
        }
        log.warn("截止提醒开关配置无效，按开启处理：{}", value);
        return true;
    }

    /** 截止前多少小时开始提醒，1～168，默认 24 */
    public int remindBeforeHours() {
        return readInt(KEY_REMIND_BEFORE_HOURS, DEFAULT_BEFORE_HOURS, 1, 168);
    }

    /** 同一任务两次催交的最小间隔分钟数，0 表示不限，最大一周，默认 60 */
    public int manualRemindIntervalMinutes() {
        return readInt(KEY_MANUAL_INTERVAL_MINUTES, DEFAULT_MANUAL_INTERVAL_MINUTES, 0, 7 * 24 * 60);
    }

    /** 同一任务两次新提交汇总的最小间隔分钟数，0 表示关闭，最大一天，默认 60 */
    public int teacherDigestMinutes() {
        return readInt(KEY_TEACHER_DIGEST_MINUTES, DEFAULT_TEACHER_DIGEST_MINUTES, 0, 24 * 60);
    }

    private int readInt(String key, int fallback, int min, int max) {
        String value = readValue(key);
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed != Math.rint(parsed) || parsed < min || parsed > max) {
                log.warn("配置 {} 无效，回退至 {}：{}", key, fallback, value);
                return fallback;
            }
            return (int) parsed;
        } catch (NumberFormatException ex) {
            log.warn("配置 {} 无效，回退至 {}：{}", key, fallback, value);
            return fallback;
        }
    }

    private String readValue(String key) {
        try {
            return configApi.getConfigValueByKey(key);
        } catch (Exception ex) {
            log.warn("读取提醒配置失败，使用默认值：{}", key);
            return null;
        }
    }

}
