package cn.nus.zknu.framework.common.util.monitor;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.MDC;

/**
 * 链路追踪工具类
 * <p>
 * 考虑到每个 starter 都需要用到该工具类，所以放到 common 模块下的 util 包下
 *
 * @author hyh
 */
public class TracerUtils {

    public static final String MDC_KEY_TRACE_ID = "traceId";

    private static final String SKYWALKING_EMPTY_TRACE_ID = "N/A";

    /**
     * 私有化构造方法
     */
    private TracerUtils() {
    }

    /**
     * 获得链路追踪编号，直接返回 SkyWalking 的 TraceId。
     * 如果不存在的话为空字符串！！！
     *
     * @return 链路追踪编号
     */
    public static String getTraceId() {
        String traceId = MDC.get(MDC_KEY_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return getSkyWalkingTraceId();
    }

    public static String getSkyWalkingTraceId() {
        String traceId = TraceContext.traceId();
        return SKYWALKING_EMPTY_TRACE_ID.equals(traceId) ? "" : traceId;
    }

}
