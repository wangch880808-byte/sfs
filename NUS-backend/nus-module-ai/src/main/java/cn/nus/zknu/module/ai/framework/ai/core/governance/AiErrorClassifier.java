package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.exception.ServiceException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 模型调用错误的分类与脱敏摘要。重试、熔断和调用日志共用同一套判断。
 */
public final class AiErrorClassifier {

    public static final String TYPE_TIMEOUT = "TIMEOUT";
    public static final String TYPE_CONNECTION = "CONNECTION";
    public static final String TYPE_RATE_LIMITED = "RATE_LIMITED";
    public static final String TYPE_SERVER_ERROR = "SERVER_ERROR";
    public static final String TYPE_CLIENT_ERROR = "CLIENT_ERROR";
    public static final String TYPE_INTERRUPTED = "INTERRUPTED";
    public static final String TYPE_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String TYPE_CIRCUIT_OPEN = "CIRCUIT_OPEN";
    public static final String TYPE_CANCELLED = "CANCELLED";
    public static final String TYPE_OTHER = "OTHER";

    static final int MAX_SUMMARY_LENGTH = 200;

    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|wss?)://\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern KEY_ASSIGNMENT = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|token|secret|password|authorization)\\s*[:=]\\s*\\S+");
    private static final Pattern SK_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_*\\-]{4,}");
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_\\-+/=*.]{24,}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private AiErrorClassifier() {
    }

    /** 是否值得重试：429、5xx、超时、连接失败。业务拒绝（额度、熔断）和其他 4xx 不重试。 */
    public static boolean isRetryable(Throwable error) {
        String type = classify(error);
        return TYPE_TIMEOUT.equals(type) || TYPE_CONNECTION.equals(type)
                || TYPE_RATE_LIMITED.equals(type) || TYPE_SERVER_ERROR.equals(type);
    }

    /** 是否记入熔断：模型服务侧不可用的信号（与可重试的错误相同，另加被中断的调用）。 */
    public static boolean isServiceFailure(Throwable error) {
        return isRetryable(error) || TYPE_INTERRUPTED.equals(classify(error));
    }

    public static String classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ServiceException) {
                return TYPE_OTHER;
            }
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                return cause instanceof HttpConnectTimeoutException ? TYPE_CONNECTION : TYPE_TIMEOUT;
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                return TYPE_CONNECTION;
            }
            if (cause instanceof InterruptedException || cause instanceof InterruptedIOException) {
                return TYPE_INTERRUPTED;
            }
            Integer status = httpStatus(cause);
            if (status != null) {
                return statusType(status);
            }
            if (cause instanceof TransientAiException) {
                return TYPE_SERVER_ERROR;
            }
            if (cause instanceof NonTransientAiException) {
                Integer leading = leadingStatus(cause.getMessage());
                return leading != null ? statusType(leading) : TYPE_CLIENT_ERROR;
            }
            if (cause instanceof ResourceAccessException || cause instanceof WebClientRequestException) {
                // 先看更深的原因能否区分超时，否则按连接失败处理
                String deeper = cause.getCause() != null ? classify(cause.getCause()) : TYPE_OTHER;
                return TYPE_TIMEOUT.equals(deeper) || TYPE_INTERRUPTED.equals(deeper) ? deeper : TYPE_CONNECTION;
            }
        }
        return TYPE_OTHER;
    }

    /** 可写入日志的错误摘要：类型、状态码和去掉地址、密钥后的简短原因。 */
    public static String summarize(Throwable error) {
        if (error == null) {
            return null;
        }
        if (error instanceof ServiceException serviceException) {
            return truncate(serviceException.getMessage());
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StringBuilder summary = new StringBuilder(classify(error));
        Integer status = findStatus(error);
        if (status != null) {
            summary.append(' ').append(status);
        }
        String message = sanitize(StrUtil.blankToDefault(root.getMessage(), ""));
        summary.append(" · ").append(root.getClass().getSimpleName());
        if (StrUtil.isNotBlank(message)) {
            summary.append(": ").append(message);
        }
        return truncate(summary.toString());
    }

    static String sanitize(String message) {
        String text = URL.matcher(message).replaceAll("[地址]");
        text = BEARER.matcher(text).replaceAll("[凭据]");
        text = KEY_ASSIGNMENT.matcher(text).replaceAll("$1=[已隐藏]");
        text = SK_KEY.matcher(text).replaceAll("[密钥]");
        text = EMAIL.matcher(text).replaceAll("[邮箱]");
        text = LONG_TOKEN.matcher(text).replaceAll("[已隐藏]");
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    private static String truncate(String text) {
        return StrUtil.maxLength(StrUtil.nullToEmpty(text), MAX_SUMMARY_LENGTH - 3);
    }

    private static Integer findStatus(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            Integer status = httpStatus(cause);
            if (status != null) {
                return status;
            }
            if (cause instanceof TransientAiException || cause instanceof NonTransientAiException) {
                Integer leading = leadingStatus(cause.getMessage());
                if (leading != null) {
                    return leading;
                }
            }
        }
        return null;
    }

    private static Integer httpStatus(Throwable cause) {
        if (cause instanceof RestClientResponseException response) {
            return response.getStatusCode().value();
        }
        if (cause instanceof WebClientResponseException response) {
            return response.getStatusCode().value();
        }
        return null;
    }

    /** Spring AI 默认把错误写成 “状态码 - 响应体”。 */
    private static Integer leadingStatus(String message) {
        if (message == null || message.length() < 3) {
            return null;
        }
        String head = message.substring(0, 3);
        if (!head.chars().allMatch(Character::isDigit)) {
            return null;
        }
        if (message.length() > 3 && Character.isDigit(message.charAt(3))) {
            return null;
        }
        return Integer.valueOf(head);
    }

    private static String statusType(int status) {
        if (status == 429) {
            return TYPE_RATE_LIMITED;
        }
        if (status == 408 || status == 504) {
            return TYPE_TIMEOUT;
        }
        if (status >= 500) {
            return TYPE_SERVER_ERROR;
        }
        return TYPE_CLIENT_ERROR;
    }

}
