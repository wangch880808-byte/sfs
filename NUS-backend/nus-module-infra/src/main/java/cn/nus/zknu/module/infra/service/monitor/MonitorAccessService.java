package cn.nus.zknu.module.infra.service.monitor;

import cn.nus.zknu.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.nus.zknu.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.module.infra.enums.monitor.MonitorTargetEnum;
import cn.nus.zknu.module.infra.enums.monitor.MonitorThemeEnum;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class MonitorAccessService {

    public static final String ACCESS_PERMISSION = "infra:monitor:access";
    private static final String TICKET_KEY_PREFIX = "infra:monitor-access:ticket:";
    private static final String SESSION_KEY_PREFIX = "infra:monitor-access:session:";
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value",
            String.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenCommonApi;
    @Resource
    private PermissionCommonApi permissionCommonApi;
    @Resource
    private MonitorAccessProperties properties;
    @Resource
    private HorizonSessionBridge horizonSessionBridge;

    private final SecureRandom secureRandom = new SecureRandom();

    public String createTicket(MonitorTargetEnum target, MonitorThemeEnum theme, Long userId, String accessToken) {
        if (!hasPermission(userId)) {
            throw new MonitorAccessDeniedException();
        }
        OAuth2AccessTokenCheckRespDTO token = checkToken(accessToken);
        if (token == null || !userId.equals(token.getUserId())) {
            throw new MonitorAuthenticationException();
        }
        String ticket = randomToken();
        TicketValue value = new TicketValue(userId, accessToken, target, theme);
        stringRedisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, JsonUtils.toJsonString(value),
                properties.getTicketTtl().toMillis(), TimeUnit.MILLISECONDS);
        return ticket;
    }

    public ConsumedSession consumeTicket(MonitorTargetEnum requestedTarget, String ticket) {
        String json = stringRedisTemplate.execute(GET_AND_DELETE_SCRIPT,
                Collections.singletonList(TICKET_KEY_PREFIX + ticket));
        TicketValue value = JsonUtils.parseObject(json, TicketValue.class);
        if (value == null || value.getTarget() != requestedTarget) {
            throw new MonitorAuthenticationException();
        }
        OAuth2AccessTokenCheckRespDTO token = checkToken(value.getAccessToken());
        if (token == null || !value.getUserId().equals(token.getUserId())) {
            throw new MonitorAuthenticationException();
        }
        if (!hasPermission(value.getUserId())) {
            throw new MonitorAccessDeniedException();
        }

        String horizonSession = requestedTarget == MonitorTargetEnum.SKYWALKING
                ? horizonSessionBridge.createSession() : null;
        String sessionId = randomToken();
        SessionValue session = new SessionValue(value.getUserId(), value.getAccessToken(), requestedTarget);
        stringRedisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, JsonUtils.toJsonString(session),
                properties.getSessionTtl().toMillis(), TimeUnit.MILLISECONDS);
        return new ConsumedSession(sessionId, redirectUrl(requestedTarget, value.getTheme()), horizonSession);
    }

    public VerifyResult verify(MonitorTargetEnum requestedTarget, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return VerifyResult.UNAUTHENTICATED;
        }
        String json = stringRedisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
        SessionValue value = JsonUtils.parseObject(json, SessionValue.class);
        if (value == null || value.getTarget() != requestedTarget) {
            return VerifyResult.UNAUTHENTICATED;
        }
        OAuth2AccessTokenCheckRespDTO token = checkToken(value.getAccessToken());
        if (token == null || !value.getUserId().equals(token.getUserId())) {
            stringRedisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
            return VerifyResult.UNAUTHENTICATED;
        }
        if (!hasPermission(value.getUserId())) {
            return VerifyResult.FORBIDDEN;
        }
        return VerifyResult.AUTHENTICATED;
    }

    public void revoke(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            stringRedisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        }
    }

    private OAuth2AccessTokenCheckRespDTO checkToken(String accessToken) {
        try {
            return accessToken == null ? null : oauth2TokenCommonApi.checkAccessToken(accessToken);
        } catch (ServiceException ex) {
            return null;
        }
    }

    private boolean hasPermission(Long userId) {
        return userId != null && permissionCommonApi.hasAnyPermissions(userId, ACCESS_PERMISSION);
    }

    private String redirectUrl(MonitorTargetEnum target, MonitorThemeEnum theme) {
        if (target != MonitorTargetEnum.SKYWALKING) {
            return target.getDefaultRedirectUrl();
        }
        String baseUrl = properties.getSkywalkingUrl();
        return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                + "__nus_theme?theme=" + theme.name().toLowerCase();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public enum VerifyResult {
        AUTHENTICATED,
        UNAUTHENTICATED,
        FORBIDDEN
    }

    public record ConsumedSession(String sessionId, String redirectUrl, String horizonSessionId) {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TicketValue {
        private Long userId;
        private String accessToken;
        private MonitorTargetEnum target;
        private MonitorThemeEnum theme;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SessionValue {
        private Long userId;
        private String accessToken;
        private MonitorTargetEnum target;
    }

    public static class MonitorAuthenticationException extends RuntimeException {
    }

    public static class MonitorAccessDeniedException extends RuntimeException {
    }

}
