package cn.nus.zknu.module.edu.service.importbatch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 导入独立线程池；由 NusAsyncAutoConfiguration 在初始化前挂载 TTL TaskDecorator。 */
@Configuration(proxyBeanMethods = false)
public class EduImportExecutorConfiguration {

    @Bean(name = "eduImportExecutor")
    public ThreadPoolTaskExecutor eduImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("edu-import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

}
