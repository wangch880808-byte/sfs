package cn.nus.zknu.framework.quartz.core.task;

import cn.nus.zknu.framework.common.util.monitor.TracerUtils;
import com.alibaba.ttl.TtlRunnable;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Propagates application context and the log trace id to worker threads.
 * SkyWalking's threading plugin propagates the actual tracing context.
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Runnable ttlRunnable = TtlRunnable.get(runnable);
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        String callerTraceId = TracerUtils.getTraceId();
        return () -> {
            Map<String, String> workerMdc = MDC.getCopyOfContextMap();
            try {
                if (callerMdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(callerMdc);
                }
                if (callerTraceId != null && !callerTraceId.isBlank()) {
                    MDC.put(TracerUtils.MDC_KEY_TRACE_ID, callerTraceId);
                }
                ttlRunnable.run();
            } finally {
                if (workerMdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(workerMdc);
                }
            }
        };
    }

}
