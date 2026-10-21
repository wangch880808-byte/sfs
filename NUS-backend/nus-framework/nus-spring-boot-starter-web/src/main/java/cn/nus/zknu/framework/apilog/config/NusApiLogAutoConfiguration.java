package cn.nus.zknu.framework.apilog.config;

import cn.nus.zknu.framework.apilog.core.filter.ApiAccessLogFilter;
import cn.nus.zknu.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.nus.zknu.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.nus.zknu.framework.common.enums.WebFilterOrderEnum;
import cn.nus.zknu.framework.web.config.NusWebAutoConfiguration;
import cn.nus.zknu.framework.web.config.WebProperties;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = NusWebAutoConfiguration.class)
public class NusApiLogAutoConfiguration implements WebMvcConfigurer {

    private static <T extends Filter> FilterRegistrationBean<T> createFilterBean(T filter, Integer order) {
        FilterRegistrationBean<T> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(order);
        return bean;
    }

    /**
     * 创建 ApiAccessLogFilter Bean，记录 API 请求日志
     */
    @Bean
    @ConditionalOnProperty(prefix = "nus.access-log", value = "enable", matchIfMissing = true)
    // 允许使用 nus.access-log.enable=false 禁用访问日志
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilter(WebProperties webProperties,
                                                                         @Value("${spring.application.name}") String applicationName,
                                                                         ApiAccessLogCommonApi apiAccessLogApi) {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(webProperties, applicationName, apiAccessLogApi);
        return createFilterBean(filter, WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiAccessLogInterceptor());
    }

}
