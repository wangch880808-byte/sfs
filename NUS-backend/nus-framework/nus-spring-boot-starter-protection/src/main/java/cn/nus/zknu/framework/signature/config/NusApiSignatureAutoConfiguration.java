package cn.nus.zknu.framework.signature.config;

import cn.nus.zknu.framework.redis.config.NusRedisAutoConfiguration;
import cn.nus.zknu.framework.signature.core.aop.ApiSignatureAspect;
import cn.nus.zknu.framework.signature.core.redis.ApiSignatureRedisDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * HTTP API 签名的自动配置类
 *
 * @author hyh
 */
@AutoConfiguration(after = NusRedisAutoConfiguration.class)
public class NusApiSignatureAutoConfiguration {

    @Bean
    public ApiSignatureAspect signatureAspect(ApiSignatureRedisDAO signatureRedisDAO) {
        return new ApiSignatureAspect(signatureRedisDAO);
    }

    @Bean
    public ApiSignatureRedisDAO signatureRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new ApiSignatureRedisDAO(stringRedisTemplate);
    }

}
