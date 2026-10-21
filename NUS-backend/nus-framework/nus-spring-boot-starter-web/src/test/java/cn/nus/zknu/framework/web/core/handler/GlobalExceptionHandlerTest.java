package cn.nus.zknu.framework.web.core.handler;

import cn.nus.zknu.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GlobalExceptionHandlerTest {

    @Test
    void importPasswordValidationDoesNotLogRejectedValue() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "importConfirm");
        binding.addError(new FieldError("importConfirm", "initialPassword", "too-long-secret-value",
                false, null, null, "初始密码长度为 4-16 位"));
        var parameter = new MethodParameter(TestController.class.getMethod("confirm", String.class), 0);
        var exception = new MethodArgumentNotValidException(parameter, binding);
        assertThat(exception.getMessage()).contains("too-long-secret-value");
        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var handler = new GlobalExceptionHandler("nus-server", mock(ApiErrorLogCommonApi.class));
            var response = handler.methodArgumentNotValidExceptionExceptionHandler(exception);
            assertThat(response.getMsg()).contains("初始密码长度为 4-16 位");
            handler.bindExceptionHandler(new BindException(binding));
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains("too-long-secret-value"));
            assertThat(appender.list).extracting(ILoggingEvent::getThrowableProxy).containsOnlyNulls();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    public static class TestController {
        public void confirm(String initialPassword) {
        }
    }

    @Test
    void asyncClientDisconnectShouldNotCreateApiErrorLog() {
        ApiErrorLogCommonApi apiErrorLogApi = mock(ApiErrorLogCommonApi.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler("nus-server", apiErrorLogApi);

        assertDoesNotThrow(() -> handler.asyncRequestNotUsableExceptionHandler(
                new AsyncRequestNotUsableException("client disconnected")));

        verifyNoInteractions(apiErrorLogApi);
    }

}
