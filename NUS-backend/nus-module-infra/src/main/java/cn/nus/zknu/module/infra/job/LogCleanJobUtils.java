package cn.nus.zknu.module.infra.job;

import cn.hutool.core.util.StrUtil;

/**
 * 日志清理 Job 的参数工具类
 */
public final class LogCleanJobUtils {

    private LogCleanJobUtils() {
    }

    /**
     * 从任务参数中解析保留天数，参数为空或不是正整数时使用默认值
     *
     * @param param       任务参数，例如 "180"
     * @param defaultDays 默认保留天数
     * @return 保留天数
     */
    public static int parseRetainDays(String param, int defaultDays) {
        String value = StrUtil.trim(param);
        if (StrUtil.isEmpty(value)) {
            return defaultDays;
        }
        try {
            int days = Integer.parseInt(value);
            return days > 0 ? days : defaultDays;
        } catch (NumberFormatException ex) {
            return defaultDays;
        }
    }

}
