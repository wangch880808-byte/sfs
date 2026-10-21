package cn.nus.zknu.module.infra.controller.admin.monitor;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.infra.controller.admin.monitor.vo.MonitorAccessTicketRespVO;
import cn.nus.zknu.module.infra.enums.monitor.MonitorTargetEnum;
import cn.nus.zknu.module.infra.enums.monitor.MonitorThemeEnum;
import cn.nus.zknu.module.infra.service.monitor.HorizonSessionBridge;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessProperties;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.ConsumedSession;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.MonitorAccessDeniedException;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.MonitorAuthenticationException;
import cn.nus.zknu.module.infra.service.monitor.MonitorAccessService.VerifyResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 监控统一访问")
@RestController
@RequestMapping("/infra/monitor-access")
public class MonitorAccessController {

    @Resource
    private MonitorAccessService monitorAccessService;
    @Resource
    private MonitorAccessProperties properties;

    @PostMapping("/ticket")
    @Operation(summary = "签发一次性监控访问票据")
    @PreAuthorize("@ss.hasPermission('infra:monitor:access')")
    public CommonResult<MonitorAccessTicketRespVO> createTicket(@RequestParam MonitorTargetEnum target,
                                                                @RequestParam(defaultValue = "LIGHT") MonitorThemeEnum theme,
                                                                HttpServletRequest request) {
        String accessToken = SecurityFrameworkUtils.obtainAuthorization(request, "Authorization", "token");
        try {
            String ticket = monitorAccessService.createTicket(
                    target, theme, SecurityFrameworkUtils.getLoginUserId(), accessToken);
            String url = "/admin-api/infra/monitor-access/consume?target=" + target + "&ticket=" + ticket;
            return success(new MonitorAccessTicketRespVO(url));
        } catch (MonitorAccessDeniedException ex) {
            throw exception(FORBIDDEN);
        } catch (MonitorAuthenticationException ex) {
            throw exception(UNAUTHORIZED);
        }
    }

    @PermitAll
    @GetMapping("/consume")
    @Operation(summary = "消费一次性票据并建立监控会话")
    public void consume(@RequestParam MonitorTargetEnum target, @RequestParam String ticket,
                        HttpServletResponse response) throws IOException {
        try {
            ConsumedSession session = monitorAccessService.consumeTicket(target, ticket);
            addCookie(response, target.getCookieName(), session.sessionId(), target.getCookiePath(),
                    properties.getSessionTtl(), properties.isCookieSecure());
            if (session.horizonSessionId() != null) {
                addCookie(response, HorizonSessionBridge.HORIZON_COOKIE_NAME, session.horizonSessionId(), "/",
                        Duration.ofMinutes(60), properties.isCookieSecure());
            }
            response.sendRedirect(session.redirectUrl());
        } catch (MonitorAccessDeniedException ex) {
            response.sendError(HttpStatus.FORBIDDEN.value());
        } catch (MonitorAuthenticationException ex) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        } catch (IllegalStateException ex) {
            response.sendError(HttpStatus.BAD_GATEWAY.value(), "监控服务暂不可用");
        }
    }

    @PermitAll
    @GetMapping("/verify")
    @Operation(summary = "供反向代理校验监控会话")
    public ResponseEntity<Void> verify(@RequestParam MonitorTargetEnum target, HttpServletRequest request) {
        VerifyResult result = monitorAccessService.verify(target, cookieValue(request, target.getCookieName()));
        return switch (result) {
            case AUTHENTICATED -> ResponseEntity.noContent().build();
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            case UNAUTHENTICATED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }

    @PermitAll
    @PostMapping("/revoke")
    @Operation(summary = "撤销当前浏览器的监控会话")
    public ResponseEntity<Void> revoke(HttpServletRequest request, HttpServletResponse response) {
        for (MonitorTargetEnum target : MonitorTargetEnum.values()) {
            monitorAccessService.revoke(cookieValue(request, target.getCookieName()));
            addCookie(response, target.getCookieName(), "", target.getCookiePath(), Duration.ZERO,
                    properties.isCookieSecure());
        }
        addCookie(response, HorizonSessionBridge.HORIZON_COOKIE_NAME, "", "/", Duration.ZERO,
                properties.isCookieSecure());
        return ResponseEntity.noContent().build();
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private static void addCookie(HttpServletResponse response, String name, String value, String path,
                                  Duration maxAge, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
