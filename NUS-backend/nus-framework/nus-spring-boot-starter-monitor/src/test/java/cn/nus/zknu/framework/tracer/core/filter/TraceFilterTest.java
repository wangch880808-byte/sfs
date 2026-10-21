package cn.nus.zknu.framework.tracer.core.filter;

import cn.nus.zknu.framework.common.util.monitor.TracerUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static cn.nus.zknu.framework.common.util.monitor.TracerUtils.MDC_KEY_TRACE_ID;
import static org.assertj.core.api.Assertions.assertThat;

class TraceFilterTest {

    private final TraceFilter filter = new TraceFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateTraceIdAndExposeItToLogsAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> traceIdInChain.set(TracerUtils.getTraceId()));

        assertThat(traceIdInChain.get()).hasSize(32).matches("[a-f0-9]+");
        assertThat(response.getHeader(TraceFilter.HEADER_NAME_TRACE_ID)).isEqualTo(traceIdInChain.get());
        assertThat(response.getHeader(TraceFilter.LEGACY_HEADER_NAME_TRACE_ID)).isEqualTo(traceIdInChain.get());
        assertThat(MDC.get(MDC_KEY_TRACE_ID)).isNull();
    }

    @Test
    void shouldReuseValidIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceFilter.HEADER_NAME_TRACE_ID, "school-deploy-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> traceIdInChain.set(TracerUtils.getTraceId()));

        assertThat(traceIdInChain.get()).isEqualTo("school-deploy-123");
    }

    @Test
    void shouldRejectUnsafeIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceFilter.HEADER_NAME_TRACE_ID, "invalid trace id\nforged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(TraceFilter.HEADER_NAME_TRACE_ID))
                .hasSize(32)
                .doesNotContain("invalid", "\\n");
    }

}
