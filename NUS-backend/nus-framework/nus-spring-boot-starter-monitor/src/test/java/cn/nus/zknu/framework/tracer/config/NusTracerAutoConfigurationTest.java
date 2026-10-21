package cn.nus.zknu.framework.tracer.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NusTracerAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NusTracerAutoConfiguration.class));

    @Test
    void shouldRegisterTraceFilterWithoutSkyWalkingAgent() {
        contextRunner.run(context -> assertThat(context).hasBean("traceFilter"));
    }

    @Test
    void shouldAllowTraceFilterToBeDisabled() {
        contextRunner.withPropertyValues("nus.tracer.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean("traceFilter"));
    }

}
