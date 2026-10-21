package cn.nus.zknu.module.system.cache;

import cn.nus.zknu.framework.redis.config.NusRedisAutoConfiguration;
import cn.nus.zknu.framework.redis.core.TimeoutRedisCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实缓存代理、TTL 解析和 JSON 序列化，仅替换 Redis 网络传输。 */
public abstract class RedisCacheTestSupport {

    protected AnnotationConfigApplicationContext context;
    protected TimeoutRedisCacheManager cacheManager;
    private long elapsedMillis;

    @BeforeEach
    void initializeCache() {
        Map<String, byte[]> values = new HashMap<>();
        Map<String, Long> expiry = new HashMap<>();
        RedisCacheWriter writer = mock(RedisCacheWriter.class);
        when(writer.get(anyString(), any(byte[].class))).thenAnswer(invocation -> {
            String key = new String(invocation.getArgument(1), StandardCharsets.UTF_8);
            if (expiry.getOrDefault(key, 0L) <= elapsedMillis) {
                values.remove(key);
            }
            return values.get(key);
        });
        doAnswer(invocation -> {
            String key = new String(invocation.getArgument(1), StandardCharsets.UTF_8);
            values.put(key, invocation.getArgument(2));
            expiry.put(key, elapsedMillis + ((Duration) invocation.getArgument(3)).toMillis());
            return null;
        }).when(writer).put(anyString(), any(byte[].class), any(byte[].class), any(Duration.class));
        doAnswer(invocation -> {
            values.remove(new String(invocation.getArgument(1), StandardCharsets.UTF_8));
            return null;
        }).when(writer).remove(anyString(), any(byte[].class));
        doAnswer(invocation -> {
            String prefix = invocation.getArgument(0) + "::";
            values.keySet().removeIf(key -> key.startsWith(prefix));
            return null;
        }).when(writer).clean(anyString(), any(byte[].class));
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        NusRedisAutoConfiguration.buildRedisSerializer()));
        cacheManager = new TimeoutRedisCacheManager(writer, config);
        cacheManager.afterPropertiesSet();
    }

    protected void startContext(Class<?>... classes) {
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("cacheManager", cacheManager);
        context.register(CachingConfiguration.class);
        context.register(classes);
        registerAdditionalBeans();
        context.refresh();
    }

    protected void registerAdditionalBeans() {
    }

    protected void assertTtl(String name, Duration ttl) {
        assertEquals(ttl, ((RedisCache) cacheManager.getCache(name)).getCacheConfiguration().getTtl());
    }

    protected void advance(Duration duration) {
        elapsedMillis += duration.toMillis();
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CachingConfiguration {
    }
}
