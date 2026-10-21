package cn.nus.zknu.module.edu.framework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EduRequestCacheTest {
    @AfterEach void tearDown() { RequestContextHolder.resetRequestAttributes(); }

    private void newRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    void cachesNullAndFalseAndSeparatesNamespacesKeysAndRequests() {
        newRequest();
        var count = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertNull(EduRequestCache.get("course", 1L, () -> { count.incrementAndGet(); return null; }));
            assertFalse(EduRequestCache.get("member", 1L, () -> { count.incrementAndGet(); return false; }));
        }
        assertEquals(2, count.get());
        assertEquals(3, EduRequestCache.get("course", 2L, count::incrementAndGet));
        newRequest();
        assertEquals(4, EduRequestCache.get("course", 1L, count::incrementAndGet));
    }

    @Test
    void exceptionsAreRetriedAndBackgroundCallsNeverCache() {
        newRequest();
        var count = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class, () -> EduRequestCache.get("x", 1L, () -> {
                count.incrementAndGet();
                throw new IllegalStateException("查询失败");
            }));
        }
        assertEquals(3, EduRequestCache.get("x", 1L, count::incrementAndGet));
        RequestContextHolder.resetRequestAttributes();
        assertEquals(4, EduRequestCache.get("x", 1L, count::incrementAndGet));
        assertEquals(5, EduRequestCache.get("x", 1L, count::incrementAndGet));
    }
}
