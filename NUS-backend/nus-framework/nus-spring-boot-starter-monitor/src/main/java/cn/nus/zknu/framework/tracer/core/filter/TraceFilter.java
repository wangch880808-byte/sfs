package cn.nus.zknu.framework.tracer.core.filter;

import cn.nus.zknu.framework.common.util.monitor.TracerUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import static cn.nus.zknu.framework.common.util.monitor.TracerUtils.MDC_KEY_TRACE_ID;

/**
 * Trace 过滤器，打印 traceId 到 header 中返回
 *
 * @author hyh
 */
public class TraceFilter extends OncePerRequestFilter {

    /**
     * Header 名 - 链路追踪编号
     */
    static final String HEADER_NAME_TRACE_ID = "X-Trace-Id";

    static final String LEGACY_HEADER_NAME_TRACE_ID = "trace-id";

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY_TRACE_ID, traceId);
        response.setHeader(HEADER_NAME_TRACE_ID, traceId);
        response.setHeader(LEGACY_HEADER_NAME_TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY_TRACE_ID);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = TracerUtils.getSkyWalkingTraceId();
        if (!isValidTraceId(traceId)) {
            traceId = request.getHeader(HEADER_NAME_TRACE_ID);
        }
        if (!isValidTraceId(traceId)) {
            traceId = request.getHeader(LEGACY_HEADER_NAME_TRACE_ID);
        }
        if (!isValidTraceId(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    private static boolean isValidTraceId(String traceId) {
        return traceId != null && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

}
