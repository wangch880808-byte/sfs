package cn.nus.zknu.module.infra.service.monitor;

import cn.nus.zknu.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.nus.zknu.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.infra.enums.monitor.MonitorTargetEnum;
import cn.nus.zknu.module.infra.enums.monitor.MonitorThemeEnum;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.MonitorAccessDeniedException;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.MonitorAuthenticationException;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.VerifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitorAccessServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MonitorAccessService service;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private OAuth2TokenCommonApi oauth2TokenCommonApi;
    @Mock
    private PermissionCommonApi permissionCommonApi;
    @Mock
    private HorizonSessionBridge horizonSessionBridge;

    private MonitorAccessProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MonitorAccessProperties();
        properties.setTicketTtl(Duration.ofSeconds(45));
        properties.setSessionTtl(Duration.ofMinutes(20));
        properties.setSkywalkingUrl("http://monitor.example:8082/");
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @Test
    void createTicketRequiresRealPermission() {
        when(permissionCommonApi.hasAnyPermissions(eq(1L), any(String[].class))).thenReturn(false);

        assertThrows(MonitorAccessDeniedException.class,
                () -> service.createTicket(MonitorTargetEnum.DRUID, MonitorThemeEnum.LIGHT, 1L, "token"));

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void consumeTicketIsTargetBoundAndOneTime() {
        MonitorAccessService.TicketValue ticket = new MonitorAccessService.TicketValue(
                1L, "token", MonitorTargetEnum.DRUID, MonitorThemeEnum.LIGHT);
        when(stringRedisTemplate.execute(any(), any(java.util.List.class)))
                .thenReturn(JsonUtils.toJsonString(ticket));

        assertThrows(MonitorAuthenticationException.class,
                () -> service.consumeTicket(MonitorTargetEnum.JAVA, "ticket"));

        verify(stringRedisTemplate).execute(any(), any(java.util.List.class));
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void consumeSkyWalkingCreatesBothSessions() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        MonitorAccessService.TicketValue ticket = new MonitorAccessService.TicketValue(
                1L, "token", MonitorTargetEnum.SKYWALKING, MonitorThemeEnum.DARK);
        when(stringRedisTemplate.execute(any(), any(java.util.List.class)))
                .thenReturn(JsonUtils.toJsonString(ticket));
        when(oauth2TokenCommonApi.checkAccessToken("token"))
                .thenReturn(new OAuth2AccessTokenCheckRespDTO().setUserId(1L));
        when(permissionCommonApi.hasAnyPermissions(eq(1L), any(String[].class))).thenReturn(true);
        when(horizonSessionBridge.createSession()).thenReturn("horizon-session");

        MonitorAccessService.ConsumedSession result = service.consumeTicket(MonitorTargetEnum.SKYWALKING, "ticket");

        assertNotNull(result.sessionId());
        assertEquals("horizon-session", result.horizonSessionId());
        assertEquals("http://monitor.example:8082/__nus_theme?theme=dark", result.redirectUrl());
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(20).toMillis()),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void verifyRejectsRevokedPlatformToken() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        MonitorAccessService.SessionValue session = new MonitorAccessService.SessionValue(
                1L, "expired-token", MonitorTargetEnum.JAVA);
        when(valueOperations.get(anyString())).thenReturn(JsonUtils.toJsonString(session));
        when(oauth2TokenCommonApi.checkAccessToken("expired-token")).thenReturn(null);

        VerifyResult result = service.verify(MonitorTargetEnum.JAVA, "session");

        assertEquals(VerifyResult.UNAUTHENTICATED, result);
        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    void verifyChecksCurrentPermission() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        MonitorAccessService.SessionValue session = new MonitorAccessService.SessionValue(
                1L, "token", MonitorTargetEnum.DRUID);
        when(valueOperations.get(anyString())).thenReturn(JsonUtils.toJsonString(session));
        when(oauth2TokenCommonApi.checkAccessToken("token"))
                .thenReturn(new OAuth2AccessTokenCheckRespDTO().setUserId(1L));
        when(permissionCommonApi.hasAnyPermissions(eq(1L), any(String[].class))).thenReturn(false);

        assertEquals(VerifyResult.FORBIDDEN, service.verify(MonitorTargetEnum.DRUID, "session"));
    }

}
