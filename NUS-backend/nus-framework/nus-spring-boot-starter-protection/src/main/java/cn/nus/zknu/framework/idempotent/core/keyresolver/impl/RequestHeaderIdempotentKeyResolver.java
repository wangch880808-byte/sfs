package cn.nus.zknu.framework.idempotent.core.keyresolver.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.nus.zknu.framework.common.util.servlet.ServletUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.IdempotentKeyResolver;
import cn.nus.zknu.framework.web.core.util.WebFrameworkUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.springframework.util.Assert;

/**
 * 基于请求头的幂等 Key 解析器。
 */
public class RequestHeaderIdempotentKeyResolver implements IdempotentKeyResolver {

    public static final String DEFAULT_HEADER_NAME = "X-Idempotency-Key";

    @Override
    public String resolver(JoinPoint joinPoint, Idempotent idempotent) {
        HttpServletRequest request = ServletUtils.getRequest();
        String headerName = StrUtil.blankToDefault(idempotent.keyArg(), DEFAULT_HEADER_NAME);
        String idempotencyKey = request != null ? request.getHeader(headerName) : null;
        Assert.hasText(idempotencyKey, "请求缺少幂等标识");

        String methodName = joinPoint.getSignature().toString();
        Long userId = WebFrameworkUtils.getLoginUserId();
        Integer userType = WebFrameworkUtils.getLoginUserType();
        return SecureUtil.md5(methodName + idempotencyKey + userId + userType);
    }

}
