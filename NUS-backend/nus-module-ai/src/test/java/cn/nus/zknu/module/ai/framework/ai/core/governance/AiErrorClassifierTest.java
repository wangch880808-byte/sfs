package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.nus.zknu.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static cn.nus.zknu.module.ai.framework.ai.core.governance.AiErrorClassifier.*;
import static org.junit.jupiter.api.Assertions.*;

class AiErrorClassifierTest {

    @Test
    void classifiesProviderAndNetworkErrors() {
        assertEquals(TYPE_RATE_LIMITED, classify(new NonTransientAiException("429 - slow down")));
        assertEquals(TYPE_CLIENT_ERROR, classify(new NonTransientAiException("401 - bad key")));
        assertEquals(TYPE_CLIENT_ERROR, classify(new NonTransientAiException("no status here")));
        assertEquals(TYPE_SERVER_ERROR, classify(new TransientAiException("503 - down")));
        assertEquals(TYPE_TIMEOUT, classify(new ResourceAccessException("x", new SocketTimeoutException("t"))));
        assertEquals(TYPE_CONNECTION, classify(new ResourceAccessException("x", new ConnectException("refused"))));
        assertEquals(TYPE_TIMEOUT, classify(new IllegalStateException(new TimeoutException("reactor timeout"))));
        assertEquals(TYPE_CLIENT_ERROR, classify(new HttpClientErrorException(HttpStatus.NOT_FOUND)));
        assertEquals(TYPE_OTHER, classify(new ServiceException(1, "额度")));
        assertFalse(isRetryable(new ServiceException(1, "额度")));
        assertTrue(isServiceFailure(new IllegalStateException(new InterruptedException())));
        assertFalse(isRetryable(new IllegalStateException(new InterruptedException())));
    }

    @Test
    void summaryHidesAddressesKeysAndIsShort() {
        String summary = summarize(new NonTransientAiException("401 - {\"error\":\"Incorrect API key provided: "
                + "sk-proj-AbCdEf1234567890abcdef, see https://platform.example.com/account/api-keys\","
                + "\"authorization\":\"Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig\",\"user\":\"teacher@example.edu.cn\"}"
                + "x".repeat(500)));
        assertTrue(summary.startsWith("CLIENT_ERROR 401 · NonTransientAiException: "), summary);
        assertFalse(summary.contains("sk-proj"), summary);
        assertFalse(summary.contains("platform.example.com"), summary);
        assertFalse(summary.contains("eyJhbGci"), summary);
        assertFalse(summary.contains("teacher@example"), summary);
        assertTrue(summary.length() <= 200, String.valueOf(summary.length()));
    }
}
