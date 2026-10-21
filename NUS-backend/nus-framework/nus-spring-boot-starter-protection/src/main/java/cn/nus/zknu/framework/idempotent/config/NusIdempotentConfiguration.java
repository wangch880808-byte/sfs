package cn.nus.zknu.framework.idempotent.config;

import cn.nus.zknu.framework.idempotent.core.aop.IdempotentAspect;
import cn.nus.zknu.framework.idempotent.core.keyresolver.IdempotentKeyResolver;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.DefaultIdempotentKeyResolver;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.UserIdempotentKeyResolver;
import cn.nus.zknu.framework.idempotent.core.redis.IdempotentRedisDAO;
import cn.nus.zknu.framework.redis.config.NusRedisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration(after = NusRedisAutoConfiguration.class)
public class NusIdempotentConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect(List<IdempotentKeyResolver> keyResolvers, IdempotentRedisDAO idempotentRedisDAO) {
        return new IdempotentAspect(keyResolvers, idempotentRedisDAO);
    }

    @Bean
    public IdempotentRedisDAO idempotentRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentRedisDAO(stringRedisTemplate);
    }

    // ========== 各种 IdempotentKeyResolver Bean ==========

    @Bean
    public DefaultIdempotentKeyResolver defaultIdempotentKeyResolver() {
        return new DefaultIdempotentKeyResolver();
    }

    @Bean
    public UserIdempotentKeyResolver userIdempotentKeyResolver() {
        return new UserIdempotentKeyResolver();
    }

    @Bean
    public ExpressionIdempotentKeyResolver expressionIdempotentKeyResolver() {
        return new ExpressionIdempotentKeyResolver();
    }

    @Bean
    public RequestHeaderIdempotentKeyResolver requestHeaderIdempotentKeyResolver() {
        return new RequestHeaderIdempotentKeyResolver();
    }

}
