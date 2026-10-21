package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.framework.quartz.config.NusAsyncAutoConfiguration;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.context.TransmittableThreadLocalSecurityContextHolderStrategy;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EduImportExecutorConfigurationTest {

    @Test
    void springBeanHasBoundedPoolAndRealTtlDecoratorCarriesLoginUserOnPrestartedWorkers() throws Exception {
        var originalStrategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContextHolder.setStrategyName(TransmittableThreadLocalSecurityContextHolderStrategy.class.getName());
        SecurityContextHolder.clearContext();
        try (var context = new AnnotationConfigApplicationContext(
                EduImportExecutorConfiguration.class, NusAsyncAutoConfiguration.class)) {
            var executor = context.getBean("eduImportExecutor", ThreadPoolTaskExecutor.class);
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(2, executor.getMaxPoolSize());
            assertEquals(50, executor.getQueueCapacity());
            assertEquals("edu-import-", executor.getThreadNamePrefix());
            // 在线程没有用户时预先创建，排除 InheritableThreadLocal 恰好继承的假阳性。
            executor.getThreadPoolExecutor().prestartAllCoreThreads();
            for (long userId : new long[]{7L, 8L}) {
                var user = new LoginUser().setId(userId).setUserType(2)
                        .setInfo(Map.of(LoginUser.INFO_KEY_DEPT_ID, "160"));
                SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
                var result = new CompletableFuture<LoginUser>();
                executor.execute(() -> result.complete(SecurityFrameworkUtils.getLoginUser()));
                SecurityContextHolder.clearContext();
                var received = result.get(5, TimeUnit.SECONDS);
                assertEquals(userId, received.getId());
                assertEquals("160", received.getInfo().get(LoginUser.INFO_KEY_DEPT_ID));
            }
            // 直接向底层线程池提交，检查 TTL 装饰器执行后恢复了工作线程的空上下文。
            var after = new CompletableFuture<Long>();
            executor.getThreadPoolExecutor().execute(() -> after.complete(SecurityFrameworkUtils.getLoginUserId()));
            assertNull(after.get(5, TimeUnit.SECONDS));
        } finally {
            SecurityContextHolder.clearContext();
            SecurityContextHolder.setContextHolderStrategy(originalStrategy);
        }
    }

}
