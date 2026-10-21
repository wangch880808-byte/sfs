package cn.nus.zknu.framework.apilog.core.util;

import cn.nus.zknu.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLogSanitizerTest {

    @Test
    void shouldSanitizeMapWithoutMutatingSource() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("username", "teacher");
        source.put("Password", "secret-value");
        source.put("api_key", "key-value");

        JsonNode sanitized = JsonUtils.parseTree(ApiLogSanitizer.sanitizeMap(source));

        assertThat(sanitized.get("username").asText()).isEqualTo("teacher");
        assertThat(sanitized.has("Password")).isFalse();
        assertThat(sanitized.has("api_key")).isFalse();
        assertThat(source).containsKeys("Password", "api_key");
    }

    @Test
    void shouldSanitizeNestedJsonCaseInsensitively() {
        String source = """
                {"username":"teacher","profile":{"accessToken":"token-value"},"CLIENT_SECRET":"secret-value"}
                """;

        JsonNode sanitized = JsonUtils.parseTree(ApiLogSanitizer.sanitizeJson(source));

        assertThat(sanitized.get("username").asText()).isEqualTo("teacher");
        assertThat(sanitized.get("profile").has("accessToken")).isFalse();
        assertThat(sanitized.has("CLIENT_SECRET")).isFalse();
    }

    @Test
    void shouldDropMalformedJsonInsteadOfLoggingRawBody() {
        assertThat(ApiLogSanitizer.sanitizeJson("password=plain-text-secret")).isNull();
    }

    @Test
    void shouldRemoveImportPasswordsFromRequestResponseAndErrorLogs() {
        JsonNode sanitized = JsonUtils.parseTree(ApiLogSanitizer.sanitizeJson(
                "{\"batchNo\":\"IMP-1\",\"initialPassword\":\"secret-value\",\"defaultInitialPassword\":\"default-value\"}"));
        assertThat(sanitized.get("batchNo").asText()).isEqualTo("IMP-1");
        assertThat(sanitized.has("initialPassword")).isFalse();
        assertThat(sanitized.has("defaultInitialPassword")).isFalse();
        assertThat(ApiLogSanitizer.sanitizeMap(Map.of("initialPassword", "secret-value")))
                .doesNotContain("secret-value");
    }

}
