package cn.nus.zknu.framework.apilog.core.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.nus.zknu.framework.apilog.core.annotation.ApiAccessLog;
import cn.nus.zknu.framework.common.util.servlet.ServletUtils;
import cn.nus.zknu.framework.common.util.spring.SpringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiAccessLogInterceptorTest {

    @Test
    void disabledRequestLoggingNeverReadsOrPrintsImportPassword() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(ApiAccessLogInterceptor.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var request = new MockHttpServletRequest("POST", "/edu/data-exchange/imports/IMP-1/confirm");
        request.setContentType("application/json");
        request.setContent("{\"initialPassword\":\"secret-value\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (var environment = mockStatic(SpringUtils.class); var servlet = mockStatic(ServletUtils.class)) {
            environment.when(SpringUtils::isProd).thenReturn(false);
            var handler = new HandlerMethod(new TestController(), TestController.class.getMethod("confirm"));
            new ApiAccessLogInterceptor().preHandle(request, new MockHttpServletResponse(), handler);
            servlet.verifyNoInteractions();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("请求参数已忽略"))
                    .noneMatch(message -> message.contains("secret-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void enabledConsoleLoggingSanitizesImportPasswords() {
        var logger = (Logger) LoggerFactory.getLogger(ApiAccessLogInterceptor.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var request = new MockHttpServletRequest("POST", "/example");
        request.setContentType("application/json");
        request.setContent("{\"batchNo\":\"IMP-1\",\"initialPassword\":\"secret-value\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (var environment = mockStatic(SpringUtils.class)) {
            environment.when(SpringUtils::isProd).thenReturn(false);
            new ApiAccessLogInterceptor().preHandle(request, new MockHttpServletResponse(), new Object());
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("IMP-1"))
                    .noneMatch(message -> message.contains("secret-value") || message.contains("initialPassword"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    public static class TestController {
        @ApiAccessLog(requestEnable = false)
        public void confirm() {
        }
    }
}
