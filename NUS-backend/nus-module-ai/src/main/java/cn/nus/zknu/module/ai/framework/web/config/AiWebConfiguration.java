package cn.nus.zknu.module.ai.framework.web.config;

import cn.nus.zknu.framework.swagger.config.NusSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ai 模块的 web 组件的 Configuration
 *
 * @author hyh
 */
@Configuration(proxyBeanMethods = false)
public class AiWebConfiguration {

    /**
     * ai 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi aiGroupedOpenApi() {
        return NusSwaggerAutoConfiguration.buildGroupedOpenApi("ai");
    }

}
