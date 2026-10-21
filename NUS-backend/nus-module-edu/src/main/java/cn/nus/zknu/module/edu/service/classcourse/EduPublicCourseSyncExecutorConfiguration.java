package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.module.infra.api.config.ConfigApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 独立于 AI 调用线程池，避免公共课同步挤占 AI 初评；容量从 infra_config 读取。 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class EduPublicCourseSyncExecutorConfiguration {

    public static final String KEY_POOL_SIZE = "edu.public-course.sync.pool-size";
    public static final String KEY_QUEUE_CAPACITY = "edu.public-course.sync.queue-capacity";

    @Bean(name = "eduPublicCourseSyncExecutor")
    public ThreadPoolTaskExecutor eduPublicCourseSyncExecutor(ConfigApi configApi) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = readCapacity(configApi, KEY_POOL_SIZE, 2, 8);
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(readCapacity(configApi, KEY_QUEUE_CAPACITY, 64, 1024));
        executor.setThreadNamePrefix("edu-public-course-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    private int readCapacity(ConfigApi configApi, String key, int fallback, int maximum) {
        try {
            String value = configApi.getConfigValueByKey(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 1 && parsed <= maximum) {
                return parsed;
            }
        } catch (Exception ex) {
            // 配置服务不可用时仍允许启动，使用有界默认容量。
        }
        log.warn("[readCapacity][公共课同步容量配置无效或不可用，使用默认值 key={}, fallback={}]", key, fallback);
        return fallback;
    }
}
