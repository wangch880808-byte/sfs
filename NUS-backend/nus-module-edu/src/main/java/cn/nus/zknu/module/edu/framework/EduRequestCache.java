package cn.nus.zknu.module.edu.framework;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 请求结束即失效，避免跨请求复用已撤销的权限；后台线程直接读取。 */
public final class EduRequestCache {
    private static final String ATTRIBUTE = EduRequestCache.class.getName();

    private EduRequestCache() { }

    public static <T> T get(String namespace, Object key, Supplier<T> loader) {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) {
            return loader.get();
        }
        synchronized (request) {
            @SuppressWarnings("unchecked")
            Map<Key, Object> values = (Map<Key, Object>) request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (values == null) {
                values = new HashMap<>();
                request.setAttribute(ATTRIBUTE, values, RequestAttributes.SCOPE_REQUEST);
            }
            Key cacheKey = new Key(namespace, key);
            // 未找到的结果也复用；异常在放入前抛出，不会留下拒绝结果。
            if (!values.containsKey(cacheKey)) {
                values.put(cacheKey, loader.get());
            }
            @SuppressWarnings("unchecked")
            T value = (T) values.get(cacheKey);
            return value;
        }
    }

    private record Key(String namespace, Object key) { }
}
