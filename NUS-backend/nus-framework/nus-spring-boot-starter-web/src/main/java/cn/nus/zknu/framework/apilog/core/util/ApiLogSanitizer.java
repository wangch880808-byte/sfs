package cn.nus.zknu.framework.apilog.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes credentials from request data before it is persisted as an API log.
 */
@Slf4j
public final class ApiLogSanitizer {

    private static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
            "password", "initialpassword", "defaultinitialpassword", "token", "accesstoken", "refreshtoken", "authorization",
            "secret", "clientsecret", "client_secret", "apikey", "api_key"
    );

    private ApiLogSanitizer() {
    }

    public static String sanitizeMap(Map<String, ?> source, String... additionalSensitiveKeys) {
        if (CollUtil.isEmpty(source)) {
            return null;
        }
        Set<String> sensitiveKeys = buildSensitiveKeys(additionalSensitiveKeys);
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!isSensitiveKey(key, sensitiveKeys)) {
                sanitized.put(key, value);
            }
        });
        return JsonUtils.toJsonString(sanitized);
    }

    public static String sanitizeJson(String source, String... additionalSensitiveKeys) {
        if (StrUtil.isEmpty(source)) {
            return null;
        }
        try {
            JsonNode rootNode = JsonUtils.getObjectMapper().readTree(source);
            sanitizeJson(rootNode, buildSensitiveKeys(additionalSensitiveKeys));
            return JsonUtils.toJsonString(rootNode);
        } catch (Exception ignored) {
            log.debug("[sanitizeJson][请求体不是有效 JSON，已忽略日志内容]");
            return null;
        }
    }

    private static Set<String> buildSensitiveKeys(String... additionalSensitiveKeys) {
        if (additionalSensitiveKeys == null || additionalSensitiveKeys.length == 0) {
            return DEFAULT_SENSITIVE_KEYS;
        }
        Set<String> sensitiveKeys = Arrays.stream(additionalSensitiveKeys)
                .filter(StrUtil::isNotBlank)
                .map(ApiLogSanitizer::normalizeKey)
                .collect(Collectors.toSet());
        sensitiveKeys.addAll(DEFAULT_SENSITIVE_KEYS);
        return sensitiveKeys;
    }

    private static void sanitizeJson(JsonNode node, Set<String> sensitiveKeys) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> sanitizeJson(child, sensitiveKeys));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.properties().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (isSensitiveKey(entry.getKey(), sensitiveKeys)) {
                iterator.remove();
            } else {
                sanitizeJson(entry.getValue(), sensitiveKeys);
            }
        }
    }

    private static boolean isSensitiveKey(String key, Set<String> sensitiveKeys) {
        return key != null && sensitiveKeys.contains(normalizeKey(key));
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

}
