package cn.nus.zknu.module.system.service.wechat;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.nus.zknu.module.system.controller.admin.auth.vo.WechatMiniProgramAuthVO.*;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.enums.social.SocialTypeEnum;
import cn.nus.zknu.module.system.framework.wechat.WechatMiniProgramClient;
import cn.nus.zknu.module.system.service.auth.AdminAuthService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.service.social.SocialUserService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.*;
import static cn.nus.zknu.module.system.enums.oauth2.OAuth2ClientConstants.CLIENT_ID_WECHAT_MINI_PROGRAM;
import static cn.nus.zknu.module.system.enums.oauth2.OAuth2ClientConstants.SCOPE_MINI_PROGRAM_STUDENT;

@Service
public class WechatMiniProgramAuthServiceImpl implements WechatMiniProgramAuthService {

    private static final String TICKET_KEY_PREFIX = "system:wechat-mini-program:qr:";
    private static final String SESSION_KEY_PREFIX = "system:wechat-mini-program:session:";
    private static final String CONSUMED_KEY_PREFIX = "system:wechat-mini-program:consumed:";
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Integer SOCIAL_TYPE = SocialTypeEnum.WECHAT_MINI_PROGRAM.getType();
    private static final Integer USER_TYPE = UserTypeEnum.ADMIN.getValue();
    private static final DefaultRedisScript<String> SCAN_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 'INVALID' end
            local data = cjson.decode(raw)
            if data.state ~= 'WAITING' and data.state ~= 'SCANNED' then return 'FORBIDDEN' end
            if data.scannedOpenid and data.scannedOpenid ~= ARGV[1] then return 'FORBIDDEN' end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return 'INVALID' end
            data.state = 'SCANNED'
            data.scannedOpenid = ARGV[1]
            local updated = cjson.encode(data)
            redis.call('SET', KEYS[1], updated, 'PX', ttl)
            return updated
            """, String.class);
    private static final DefaultRedisScript<String> CONFIRM_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 'INVALID' end
            local data = cjson.decode(raw)
            if data.state ~= 'SCANNED' or data.scannedOpenid ~= ARGV[1] then return 'FORBIDDEN' end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return 'INVALID' end
            data.state = 'CONFIRMED'
            data.confirmedUserId = tonumber(ARGV[2])
            local updated = cjson.encode(data)
            redis.call('SET', KEYS[1], updated, 'PX', ttl)
            return updated
            """, String.class);
    private static final DefaultRedisScript<String> EXCHANGE_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 'INVALID' end
            local data = cjson.decode(raw)
            if data.browserTokenHash ~= ARGV[1] then return 'FORBIDDEN' end
            if data.purpose ~= 'LOGIN' then return 'FORBIDDEN' end
            if data.state ~= 'CONFIRMED' or not data.confirmedUserId then return 'NOT_CONFIRMED' end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return 'INVALID' end
            if redis.call('SET', KEYS[2], '1', 'PX', ttl, 'NX') == false then return 'CONSUMED' end
            data.state = 'CONSUMED'
            local updated = cjson.encode(data)
            redis.call('SET', KEYS[1], updated, 'PX', ttl)
            return updated
            """, String.class);

    @Resource
    private WechatMiniProgramClient wechatClient;
    @Resource
    private SocialUserService socialUserService;
    @Resource
    private AdminAuthService adminAuthService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public QrCreateRespVO createLoginQrCode() {
        return createQrCode("LOGIN", null);
    }

    @Override
    public QrCreateRespVO createBindQrCode(Long userId) {
        validateActiveUser(userId);
        return createQrCode("BIND", userId);
    }

    @Override
    public ConfigRespVO getConfig() {
        ConfigRespVO response = new ConfigRespVO();
        response.setEnabled(wechatClient.isConfigured());
        return response;
    }

    private QrCreateRespVO createQrCode(String purpose, Long targetUserId) {
        String ticket = IdUtil.fastSimpleUUID();
        String browserToken = IdUtil.fastSimpleUUID();
        byte[] qrCode = wechatClient.createUnlimitedQrCode(ticket);
        long expiresAt = System.currentTimeMillis() + TICKET_TTL.toMillis();
        TicketData data = new TicketData();
        data.setTicket(ticket);
        data.setBrowserTokenHash(sha256(browserToken));
        data.setPurpose(purpose);
        data.setState("WAITING");
        data.setTargetUserId(targetUserId);
        data.setExpiresAt(expiresAt);
        writeTicket(data);

        QrCreateRespVO response = new QrCreateRespVO();
        response.setTicket(ticket);
        response.setBrowserToken(browserToken);
        response.setPurpose(purpose);
        String mediaType = qrCode[0] == (byte) 0x89 ? "image/png" : "image/jpeg";
        response.setQrCodeDataUrl("data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(qrCode));
        response.setExpiresAt(toLocalDateTime(expiresAt));
        return response;
    }

    @Override
    public QrStatusRespVO getQrStatus(QrStatusReqVO reqVO) {
        TicketData ticket = getTicket(reqVO.getTicket());
        validateBrowserToken(ticket, reqVO.getBrowserToken());
        return toStatus(ticket);
    }

    @Override
    public AuthLoginRespVO exchangeQrLogin(QrExchangeReqVO reqVO) {
        String result = stringRedisTemplate.execute(EXCHANGE_SCRIPT,
                List.of(TICKET_KEY_PREFIX + reqVO.getTicket(), CONSUMED_KEY_PREFIX + reqVO.getTicket()),
                sha256(reqVO.getBrowserToken()));
        TicketData ticket = parseScriptTicket(result);
        return adminAuthService.loginByTrustedUserId(ticket.getConfirmedUserId());
    }

    @Override
    public SessionRespVO loginByCode(String code) {
        WechatMiniProgramClient.CodeSession codeSession = wechatClient.code2Session(code);
        String miniSessionToken = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        MiniSessionData miniSession = new MiniSessionData();
        miniSession.setOpenid(codeSession.getOpenid());
        miniSession.setUnionid(codeSession.getUnionid());
        stringRedisTemplate.opsForValue().set(SESSION_KEY_PREFIX + miniSessionToken,
                JsonUtils.toJsonString(miniSession), SESSION_TTL);

        SessionRespVO response = new SessionRespVO();
        response.setMiniSessionToken(miniSessionToken);
        Long userId = socialUserService.getBoundUserId(USER_TYPE, SOCIAL_TYPE, codeSession.getOpenid());
        response.setBound(userId != null);
        if (userId != null) {
            response.setAccount(buildAccount(userId));
            CapabilitiesVO capabilities = buildCapabilities(userId);
            response.setCapabilities(capabilities);
            // 非学生仍可扫码确认网页登录，但小程序本身不签发平台管理端令牌。
            if (Boolean.TRUE.equals(capabilities.getStudentHomework())) {
                response.setLogin(adminAuthService.loginByTrustedUserId(userId,
                        CLIENT_ID_WECHAT_MINI_PROGRAM, List.of(SCOPE_MINI_PROGRAM_STUDENT)));
            }
        } else {
            CapabilitiesVO capabilities = new CapabilitiesVO();
            capabilities.setScanLogin(false);
            capabilities.setStudentHomework(false);
            response.setCapabilities(capabilities);
        }
        return response;
    }

    @Override
    public QrStatusRespVO scanQrCode(QrActionReqVO reqVO) {
        MiniSessionData miniSession = getMiniSession(reqVO.getMiniSessionToken());
        String result = stringRedisTemplate.execute(SCAN_SCRIPT,
                Collections.singletonList(TICKET_KEY_PREFIX + reqVO.getTicket()), miniSession.getOpenid());
        TicketData ticket = parseScriptTicket(result);
        return toStatus(ticket);
    }

    @Override
    public QrStatusRespVO confirmQrCode(QrActionReqVO reqVO) {
        MiniSessionData miniSession = getMiniSession(reqVO.getMiniSessionToken());
        TicketData ticket = getTicket(reqVO.getTicket());
        if (!"SCANNED".equals(ticket.getState())
                || !MessageDigest.isEqual(ticket.getScannedOpenid().getBytes(StandardCharsets.UTF_8),
                miniSession.getOpenid().getBytes(StandardCharsets.UTF_8))) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_FORBIDDEN);
        }
        Long userId;
        if ("BIND".equals(ticket.getPurpose())) {
            userId = ticket.getTargetUserId();
            validateActiveUser(userId);
            socialUserService.bindSocialIdentity(userId, USER_TYPE, buildSocialIdentity(miniSession));
        } else if ("LOGIN".equals(ticket.getPurpose())) {
            userId = socialUserService.getBoundUserId(USER_TYPE, SOCIAL_TYPE, miniSession.getOpenid());
            if (userId == null) {
                throw exception(WECHAT_MINI_PROGRAM_NOT_BOUND);
            }
            validateActiveUser(userId);
        } else {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_FORBIDDEN);
        }
        String result = stringRedisTemplate.execute(CONFIRM_SCRIPT,
                Collections.singletonList(TICKET_KEY_PREFIX + reqVO.getTicket()),
                miniSession.getOpenid(), String.valueOf(userId));
        ticket = parseScriptTicket(result);
        return toStatus(ticket);
    }

    @Override
    public BindingRespVO getBinding(Long userId) {
        String openid = socialUserService.getBoundOpenid(userId, USER_TYPE, SOCIAL_TYPE);
        BindingRespVO response = new BindingRespVO();
        response.setBound(StrUtil.isNotBlank(openid));
        response.setOpenidMasked(maskOpenid(openid));
        return response;
    }

    @Override
    public void unbind(Long userId) {
        socialUserService.unbindSocialUser(userId, USER_TYPE, SOCIAL_TYPE);
    }

    private SocialUserDO buildSocialIdentity(MiniSessionData miniSession) {
        return SocialUserDO.builder()
                .type(SOCIAL_TYPE)
                .openid(miniSession.getOpenid())
                .token("")
                .rawTokenInfo("{}")
                .nickname("微信小程序用户")
                .avatar("")
                .rawUserInfo(JsonUtils.toJsonString(Map.of(
                        "unionid", StrUtil.nullToEmpty(miniSession.getUnionid()))))
                .code("")
                .state("")
                .build();
    }

    private TicketData getTicket(String ticketId) {
        String json = stringRedisTemplate.opsForValue().get(TICKET_KEY_PREFIX + ticketId);
        TicketData ticket = JsonUtils.parseObject(json, TicketData.class);
        if (ticket == null || ticket.getExpiresAt() <= System.currentTimeMillis()) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_INVALID);
        }
        return ticket;
    }

    private void writeTicket(TicketData ticket) {
        long remainingMillis = ticket.getExpiresAt() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_INVALID);
        }
        stringRedisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket.getTicket(),
                JsonUtils.toJsonString(ticket), remainingMillis, TimeUnit.MILLISECONDS);
    }

    private MiniSessionData getMiniSession(String miniSessionToken) {
        MiniSessionData session = JsonUtils.parseObject(
                stringRedisTemplate.opsForValue().get(SESSION_KEY_PREFIX + miniSessionToken), MiniSessionData.class);
        if (session == null || StrUtil.isBlank(session.getOpenid())) {
            throw exception(WECHAT_MINI_PROGRAM_SESSION_INVALID);
        }
        return session;
    }

    private void validateBrowserToken(TicketData ticket, String browserToken) {
        if (!MessageDigest.isEqual(ticket.getBrowserTokenHash().getBytes(StandardCharsets.UTF_8),
                sha256(browserToken).getBytes(StandardCharsets.UTF_8))) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_FORBIDDEN);
        }
    }

    private TicketData parseScriptTicket(String result) {
        if ("INVALID".equals(result) || StrUtil.isBlank(result)) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_INVALID);
        }
        if ("FORBIDDEN".equals(result)) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_FORBIDDEN);
        }
        if ("NOT_CONFIRMED".equals(result)) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_NOT_CONFIRMED);
        }
        if ("CONSUMED".equals(result)) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_CONSUMED);
        }
        TicketData ticket = JsonUtils.parseObject(result, TicketData.class);
        if (ticket == null) {
            throw exception(WECHAT_MINI_PROGRAM_TICKET_INVALID);
        }
        return ticket;
    }

    private AdminUserDO validateActiveUser(Long userId) {
        AdminUserDO user = userId != null ? adminUserService.getUser(userId) : null;
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return user;
    }

    private AccountVO buildAccount(Long userId) {
        AdminUserDO user = validateActiveUser(userId);
        AccountVO account = new AccountVO();
        account.setUserId(user.getId());
        account.setUsername(user.getUsername());
        account.setNickname(user.getNickname());
        account.setAvatar(user.getAvatar());
        return account;
    }

    private CapabilitiesVO buildCapabilities(Long userId) {
        CapabilitiesVO capabilities = new CapabilitiesVO();
        capabilities.setScanLogin(true);
        capabilities.setStudentHomework(eduRoleApi.isStudent(userId));
        return capabilities;
    }

    private QrStatusRespVO toStatus(TicketData ticket) {
        QrStatusRespVO response = new QrStatusRespVO();
        response.setState(ticket.getState());
        response.setPurpose(ticket.getPurpose());
        response.setExpiresAt(toLocalDateTime(ticket.getExpiresAt()));
        if (ticket.getConfirmedUserId() != null) {
            response.setAccountNickname(validateActiveUser(ticket.getConfirmedUserId()).getNickname());
        } else if ("BIND".equals(ticket.getPurpose()) && ticket.getTargetUserId() != null) {
            response.setAccountNickname(validateActiveUser(ticket.getTargetUserId()).getNickname());
        }
        return response;
    }

    private static String maskOpenid(String openid) {
        if (StrUtil.isBlank(openid)) {
            return null;
        }
        if (openid.length() <= 8) {
            return "****";
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    @Data
    private static class TicketData {
        private String ticket;
        private String browserTokenHash;
        private String purpose;
        private String state;
        private Long targetUserId;
        private String scannedOpenid;
        private Long confirmedUserId;
        private long expiresAt;
    }

    @Data
    private static class MiniSessionData {
        private String openid;
        private String unionid;
    }

}
