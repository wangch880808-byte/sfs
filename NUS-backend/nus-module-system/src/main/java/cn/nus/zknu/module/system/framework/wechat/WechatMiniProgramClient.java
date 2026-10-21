package cn.nus.zknu.module.system.framework.wechat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialClientDO;
import cn.nus.zknu.module.system.enums.social.SocialTypeEnum;
import cn.nus.zknu.module.system.service.social.SocialClientService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.*;

/**
 * 微信小程序服务端 API 客户端，只封装 code2Session 与小程序码生成。
 */
@Component
public class WechatMiniProgramClient {

    public static final String CONFIG_QR_PAGE = "qrPage";
    public static final String CONFIG_ENV_VERSION = "envVersion";
    public static final String DEFAULT_QR_PAGE = "pages/auth/scan/index";
    public static final String DEFAULT_ENV_VERSION = "develop";
    private static final String ACCESS_TOKEN_KEY_PREFIX = "system:wechat-mini-program:access-token:";
    private static final String API_BASE = "https://api.weixin.qq.com";

    @Resource
    private SocialClientService socialClientService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CodeSession code2Session(String code) {
        ClientConfig config = getRequiredConfig();
        String url = API_BASE + "/sns/jscode2session?appid=" + encode(config.appId())
                + "&secret=" + encode(config.appSecret())
                + "&js_code=" + encode(code) + "&grant_type=authorization_code";
        String body = getText(url);
        CodeSession result = JsonUtils.parseObject(body, CodeSession.class);
        if (result == null || result.getErrcode() != null && result.getErrcode() != 0
                || StrUtil.isBlank(result.getOpenid())) {
            throw exception(WECHAT_MINI_PROGRAM_CODE_INVALID);
        }
        return result;
    }

    public byte[] createUnlimitedQrCode(String scene) {
        ClientConfig config = getRequiredConfig();
        String accessToken = getAccessToken(config);
        String url = API_BASE + "/wxa/getwxacodeunlimit?access_token=" + encode(accessToken);
        byte[] body = postJson(url, JsonUtils.toJsonString(Map.of(
                "scene", scene,
                "page", config.qrPage(),
                "check_path", false,
                "env_version", config.envVersion(),
                "width", 430
        )));
        if (!isImage(body)) {
            WechatError error = JsonUtils.parseObject(body, WechatError.class);
            throw exception(WECHAT_MINI_PROGRAM_API_ERROR,
                    error != null ? StrUtil.blankToDefault(error.getErrmsg(), "生成小程序码失败") : "生成小程序码失败");
        }
        return body;
    }

    private static boolean isImage(byte[] body) {
        if (body == null || body.length < 4) {
            return false;
        }
        boolean png = body.length >= 8
                && body[0] == (byte) 0x89 && body[1] == 0x50
                && body[2] == 0x4E && body[3] == 0x47;
        boolean jpeg = body[0] == (byte) 0xFF && body[1] == (byte) 0xD8
                && body[2] == (byte) 0xFF;
        return png || jpeg;
    }

    private String getAccessToken(ClientConfig config) {
        String cacheKey = ACCESS_TOKEN_KEY_PREFIX
                + DigestUtil.sha256Hex(config.appId() + "\0" + config.appSecret());
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        String url = API_BASE + "/cgi-bin/token?grant_type=client_credential&appid=" + encode(config.appId())
                + "&secret=" + encode(config.appSecret());
        AccessTokenResponse response = JsonUtils.parseObject(getText(url), AccessTokenResponse.class);
        if (response == null || StrUtil.isBlank(response.getAccessToken())) {
            throw exception(WECHAT_MINI_PROGRAM_API_ERROR,
                    response != null ? StrUtil.blankToDefault(response.getErrmsg(), "获取 access_token 失败")
                            : "获取 access_token 失败");
        }
        long ttl = Math.max(60, response.getExpiresIn() - 300L);
        stringRedisTemplate.opsForValue().set(cacheKey, response.getAccessToken(), ttl, TimeUnit.SECONDS);
        return response.getAccessToken();
    }

    private String getText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw exception(WECHAT_MINI_PROGRAM_API_ERROR, "HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(WECHAT_MINI_PROGRAM_API_ERROR, "请求被中断");
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            // 不透传底层异常文本，避免 URI 异常等场景意外包含 AppSecret。
            throw exception(WECHAT_MINI_PROGRAM_API_ERROR, "微信服务连接失败");
        }
    }

    private byte[] postJson(String url, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw exception(WECHAT_MINI_PROGRAM_API_ERROR, "HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(WECHAT_MINI_PROGRAM_API_ERROR, "请求被中断");
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(WECHAT_MINI_PROGRAM_API_ERROR, "微信服务连接失败");
        }
    }

    public boolean isConfigured() {
        SocialClientDO client = socialClientService.getSocialClient(
                SocialTypeEnum.WECHAT_MINI_PROGRAM.getType(), UserTypeEnum.ADMIN.getValue());
        return client != null && CommonStatusEnum.isEnable(client.getStatus())
                && StrUtil.isNotBlank(client.getClientId()) && StrUtil.isNotBlank(client.getClientSecret());
    }

    private ClientConfig getRequiredConfig() {
        SocialClientDO client = socialClientService.getSocialClient(
                SocialTypeEnum.WECHAT_MINI_PROGRAM.getType(), UserTypeEnum.ADMIN.getValue());
        if (client == null || !CommonStatusEnum.isEnable(client.getStatus())
                || StrUtil.isBlank(client.getClientId()) || StrUtil.isBlank(client.getClientSecret())) {
            throw exception(WECHAT_MINI_PROGRAM_NOT_CONFIGURED);
        }
        Map<String, String> extra = client.getConfig();
        String qrPage = extra != null ? extra.get(CONFIG_QR_PAGE) : null;
        String envVersion = extra != null ? extra.get(CONFIG_ENV_VERSION) : null;
        return new ClientConfig(client.getClientId(), client.getClientSecret(),
                StrUtil.blankToDefault(qrPage, DEFAULT_QR_PAGE),
                StrUtil.blankToDefault(envVersion, DEFAULT_ENV_VERSION));
    }

    private static String encode(String value) {
        return URLEncoder.encode(StrUtil.nullToEmpty(value), StandardCharsets.UTF_8);
    }

    private record ClientConfig(String appId, String appSecret, String qrPage, String envVersion) {
    }

    @Data
    public static class CodeSession {
        private String openid;
        @JsonProperty("session_key")
        private String sessionKey;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }

    @Data
    private static class AccessTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private int expiresIn;
        private Integer errcode;
        private String errmsg;
    }

    @Data
    private static class WechatError {
        private Integer errcode;
        private String errmsg;
    }

}
