package cn.nus.zknu.module.ai.framework.ai.config;

import cn.nus.zknu.module.ai.framework.ai.core.model.AiModelFactory;
import cn.nus.zknu.module.ai.framework.ai.core.model.AiModelFactoryImpl;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 自动配置
 */
@Configuration
@EnableConfigurationProperties(NusAiProperties.class)
public class AiAutoConfiguration {

    @Bean
    public AiModelFactory aiModelFactory(NusAiProperties properties) {
        return new AiModelFactoryImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }


}
