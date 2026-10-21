package cn.nus.zknu.module.edu.service.semester;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 学期流转相关配置：公共课补发与旧学期公共课关联封存开关。
 * 配置缺失或非法时回退默认值，写法与 EduMetricPolicy 一致。
 */
@Service
@Slf4j
public class EduTermFlowPolicy {

    public static final String KEY_PUBLIC_COURSE_BACKFILL = "edu.public-course.backfill-enabled";
    public static final String KEY_PUBLIC_COURSE_ARCHIVE_PREVIOUS = "edu.public-course.archive-previous-semester";

    @Resource
    private ConfigApi configApi;

    /** 班级新加入公共课时，是否补发进行中的全课程任务并发送课程分配通知。 */
    public boolean publicCourseBackfillEnabled() {
        return readBoolean(KEY_PUBLIC_COURSE_BACKFILL, true);
    }

    /** 学期切换后，是否封存旧学期中由同步自动创建且已无进行中任务的公共课关联。 */
    public boolean archivePreviousSemesterPublicLinks() {
        return readBoolean(KEY_PUBLIC_COURSE_ARCHIVE_PREVIOUS, true);
    }

    private boolean readBoolean(String key, boolean fallback) {
        String value = read(key);
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        log.warn("开关配置无效，回退至 {}：{}={}", fallback, key, value);
        return fallback;
    }

    private String read(String key) {
        try {
            return configApi.getConfigValueByKey(key);
        } catch (Exception ex) {
            log.warn("读取学期流转配置失败，使用默认值：{}", key);
            return null;
        }
    }
}
