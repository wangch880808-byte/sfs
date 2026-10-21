package cn.nus.zknu.module.ai.framework.ai.core.governance;

import org.springframework.web.context.request.RequestContextHolder;

import java.util.function.Supplier;

/**
 * 一次模型调用的业务上下文，由调用方在调用模型前放到当前线程，供调用日志、额度和熔断使用。
 *
 * @param businessCode 业务码
 * @param bizId        业务对象编号，可为空
 * @param operatorId   发起人，可为空（系统任务）
 * @param background   是否后台任务发起；后台调用在并发已满时排队等待，页面请求直接提示繁忙；
 *                     后台调用不计入、也不受每人每日调用次数限制，只计入全平台令牌额度
 * @param governed     是否受额度和熔断限制；模型连接测试不受限制，只记录日志
 */
public record AiCallContext(String businessCode, String bizId, Long operatorId, boolean background, boolean governed) {

    private static final ThreadLocal<AiCallContext> HOLDER = new ThreadLocal<>();

    public static AiCallContext of(String businessCode, String bizId, Long operatorId) {
        return new AiCallContext(businessCode, bizId, operatorId, isBackgroundThread(), true);
    }

    public static AiCallContext current() {
        return HOLDER.get();
    }

    public static <T> T call(AiCallContext context, Supplier<T> action) {
        AiCallContext previous = HOLDER.get();
        HOLDER.set(context);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    /**
     * 当前线程是否不在 HTTP 请求里：线程池、定时任务里的调用视为后台调用。
     * 请求上下文不会传到线程池，所以无需调用方显式声明。
     */
    public static boolean isBackgroundThread() {
        return RequestContextHolder.getRequestAttributes() == null;
    }

}
