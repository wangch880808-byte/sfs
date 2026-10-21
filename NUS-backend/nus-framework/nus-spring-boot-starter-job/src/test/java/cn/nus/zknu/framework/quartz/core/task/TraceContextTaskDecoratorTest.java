package cn.nus.zknu.framework.quartz.core.task;

import cn.nus.zknu.framework.common.util.monitor.TracerUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTaskDecoratorTest {

    private final TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPropagateCallerTraceIdAndRestoreWorkerContext() {
        MDC.put(TracerUtils.MDC_KEY_TRACE_ID, "caller-trace");
        AtomicReference<String> traceIdInTask = new AtomicReference<>();
        Runnable decorated = decorator.decorate(
                () -> traceIdInTask.set(MDC.get(TracerUtils.MDC_KEY_TRACE_ID)));

        MDC.put(TracerUtils.MDC_KEY_TRACE_ID, "worker-trace");
        decorated.run();

        assertThat(traceIdInTask.get()).isEqualTo("caller-trace");
        assertThat(MDC.get(TracerUtils.MDC_KEY_TRACE_ID)).isEqualTo("worker-trace");
    }

}
