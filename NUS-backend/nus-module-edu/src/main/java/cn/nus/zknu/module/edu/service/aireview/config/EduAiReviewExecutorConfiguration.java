package cn.nus.zknu.module.edu.service.aireview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class EduAiReviewExecutorConfiguration {

    @Bean(name = "eduAiReviewTaskExecutor")
    public Executor eduAiReviewTaskExecutor(EduAiReviewProperties properties) {
        int poolSize = Math.max(1, properties.getTaskPoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Math.max(1, properties.getTaskQueueCapacity()));
        executor.setThreadNamePrefix("edu-ai-review-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

}
