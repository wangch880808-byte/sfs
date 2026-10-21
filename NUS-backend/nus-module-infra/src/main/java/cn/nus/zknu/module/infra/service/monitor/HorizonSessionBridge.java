package cn.nus.zknu.module.infra.service.monitor;

import cn.nus.zknu.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class HorizonSessionBridge {

    public static final String HORIZON_COOKIE_NAME = "horizon_sid";

    @Resource
    private MonitorAccessProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String createSession() {
        if (properties.getHorizonUsername() == null || properties.getHorizonPassword() == null) {
            throw new IllegalStateException("Horizon bridge credentials are not configured");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stripTrailingSlash(properties.getHorizonInternalUrl()) + "/api/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(Map.of(
                        "username", properties.getHorizonUsername(),
                        "password", properties.getHorizonPassword()))))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Horizon session bridge rejected the internal login, status={}", response.statusCode());
                throw new IllegalStateException("Horizon session bridge rejected the internal login");
            }
            return response.headers().allValues("set-cookie").stream()
                    .flatMap(header -> HttpCookie.parse(header).stream())
                    .filter(cookie -> HORIZON_COOKIE_NAME.equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Horizon login did not return a session cookie"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Horizon session bridge was interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Horizon session bridge is unavailable", ex);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}
