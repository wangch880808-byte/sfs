package cn.nus.zknu.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    @Test
    void disablesEagerAiModelAutoConfiguration() throws Exception {
        var propertySource = loadProductionProperties();

        assertThat(propertySource.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(propertySource.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(propertySource.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(propertySource.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
        assertThat(propertySource.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(propertySource.getProperty("spring.ai.model.moderation")).isEqualTo("none");
    }

    @Test
    void allowsDruidThroughTheInternalReverseProxyOnly() throws Exception {
        var propertySource = loadProductionProperties();

        assertThat(propertySource.getProperty("spring.datasource.druid.stat-view-servlet.allow"))
                .isEqualTo("${NUS_DRUID_ALLOW:127.0.0.1,172.16.0.0/12}");
        assertThat(propertySource.getProperty("spring.datasource.druid.stat-view-servlet.reset-enable"))
                .isEqualTo(false);
    }

    @Test
    void disablesWebSocketAndApiEncryptionByDefault() throws Exception {
        var baseProperties = loadBaseProperties();

        assertThat(findProperty(baseProperties, "nus.websocket.enable")).isNull();
        assertThat(findProperty(baseProperties, "nus.api-encrypt.enable")).isEqualTo(false);
        assertThat(loadProductionProperties().getProperty("nus.api-encrypt.enable"))
                .isEqualTo("${NUS_API_ENCRYPT_ENABLED:false}");
    }

    @Test
    void doesNotDefaultToLocalProfileOrFixedSmsCode() throws Exception {
        var baseProperties = loadBaseProperties();

        assertThat(findProperty(baseProperties, "spring.profiles.active")).isNull();
        assertThat(findProperty(baseProperties, "nus.sms-code.begin-code")).isNull();
    }

    @Test
    void localProfileUsesLoopbackDefaultsAndKeepsMockLoginOff() throws Exception {
        var localProperties = new YamlPropertySourceLoader()
                .load("application-local", new ClassPathResource("application-local.yaml"));
        String content = new ClassPathResource("application-local.yaml").getContentAsString(StandardCharsets.UTF_8);

        assertThat(String.valueOf(findProperty(localProperties,
                "spring.datasource.dynamic.datasource.master.url"))).contains("${NUS_LOCAL_DB_HOST:127.0.0.1}");
        assertThat(findProperty(localProperties, "spring.datasource.dynamic.datasource.master.password"))
                .isEqualTo("${NUS_LOCAL_DB_PASSWORD:}");
        assertThat(findProperty(localProperties, "nus.security.mock-enable"))
                .isEqualTo("${NUS_SECURITY_MOCK_ENABLE:false}");
        assertThat(content).doesNotContain("119.29.", "jdbc:postgresql", "jdbc:oracle", "/Users/");
    }

    private static List<PropertySource<?>> loadBaseProperties() throws Exception {
        return new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
    }

    private static Object findProperty(List<PropertySource<?>> propertySources, String name) {
        return propertySources.stream()
                .map(propertySource -> propertySource.getProperty(name))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static org.springframework.core.env.PropertySource<?> loadProductionProperties() throws Exception {
        return new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yaml"))
                .get(0);
    }
}
