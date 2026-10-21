package cn.nus.zknu.module.system.framework.security;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.AUTH_PASSWORD_CHANGE_REQUIRED;

/** 学生首次 Web 登录后的接口限制。 */
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    private static final Integer STUDENT_USER_TYPE = 2;

    private static final Map<String, Set<String>> ALLOWED_PATHS = Map.of(
            "POST", Set.of("/admin-api/system/auth/logout", "/admin-api/system/auth/refresh-token"),
            "GET", Set.of("/admin-api/system/auth/get-permission-info", "/admin-api/system/dict-data/simple-list"),
            "PUT", Set.of("/admin-api/edu/student/profile/password", "/admin-api/system/user/profile/update-password"));

    @Resource
    private AdminUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)
                || method.hasMethodAnnotation(PermitAll.class)
                || method.getBeanType().isAnnotationPresent(PermitAll.class)
                || ALLOWED_PATHS.getOrDefault(request.getMethod(), Set.of()).contains(request.getServletPath())) {
            return true;
        }
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || !UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())
                || loginUser.getScopes() != null
                && loginUser.getScopes().contains(OAuth2ClientConstants.SCOPE_MINI_PROGRAM_STUDENT)) {
            return true;
        }
        AdminUserDO user = userService.getUser(loginUser.getId());
        if (user != null && STUDENT_USER_TYPE.equals(user.getUserType())
                && Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
            throw exception(AUTH_PASSWORD_CHANGE_REQUIRED);
        }
        return true;
    }

}
