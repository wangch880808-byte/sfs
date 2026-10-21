package cn.nus.zknu.module.system.service.user;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 仅在当前请求、当前登录用户内复用；异步线程没有请求时直接计算。 */
public final class UserDeptScopeRequestCache {
    private UserDeptScopeRequestCache() { }

    public static <T> T get(String attribute, Long userId, Supplier<T> loader) {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null || userId == null) return loader.get();
        synchronized (request) {
            @SuppressWarnings("unchecked")
            Map<Long, T> values = (Map<Long, T>) request.getAttribute(attribute, RequestAttributes.SCOPE_REQUEST);
            if (values == null) {
                values = new HashMap<>();
                request.setAttribute(attribute, values, RequestAttributes.SCOPE_REQUEST);
            }
            // 异常不缓存，保持原有拒绝行为。
            return values.computeIfAbsent(userId, ignored -> loader.get());
        }
    }
}
