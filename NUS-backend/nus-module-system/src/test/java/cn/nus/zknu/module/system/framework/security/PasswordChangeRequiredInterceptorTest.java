package cn.nus.zknu.module.system.framework.security;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.auth.AuthController;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.AUTH_PASSWORD_CHANGE_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PasswordChangeRequiredInterceptorTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PasswordChangeRequiredInterceptor interceptor;
    @Mock
    private AdminUserService userService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @Test
    void requiredWebStudent_isBlocked() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/admin-api/edu/student/profile/get");
        when(userService.getUser(1L)).thenReturn(user(2, true));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(null));
            assertServiceException(() -> interceptor.preHandle(request, response, handler("getPermissionInfo")),
                    AUTH_PASSWORD_CHANGE_REQUIRED);
        }
    }

    @Test
    void whitelistAndPermitAll_areAllowed() throws Exception {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(null));
            String[][] paths = {
                    {"POST", "/admin-api/system/auth/logout"},
                    {"POST", "/admin-api/system/auth/refresh-token"},
                    {"GET", "/admin-api/system/auth/get-permission-info"},
                    {"GET", "/admin-api/system/dict-data/simple-list"},
                    {"PUT", "/admin-api/edu/student/profile/password"},
                    {"PUT", "/admin-api/system/user/profile/update-password"}
            };
            for (String[] path : paths) {
                when(request.getMethod()).thenReturn(path[0]);
                when(request.getServletPath()).thenReturn(path[1]);
                assertTrue(interceptor.preHandle(request, response, handler("getPermissionInfo")));
            }
            // @PermitAll 接口在读取请求路径之前就会放行
            assertTrue(interceptor.preHandle(request, response, handler("getSocialLoginConfig")));
            verifyNoInteractions(userService);
        }
    }

    @Test
    void miniappTeacherAndChangedStudent_areAllowed() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/admin-api/edu/student/profile/get");
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(List.of("miniapp:student")));
            assertTrue(interceptor.preHandle(request, response, handler("getPermissionInfo")));
            security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(null));
            when(userService.getUser(1L)).thenReturn(user(1, true), user(2, false));
            assertTrue(interceptor.preHandle(request, response, handler("getPermissionInfo")));
            assertTrue(interceptor.preHandle(request, response, handler("getPermissionInfo")));
        }
    }

    private HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new AuthController(), AuthController.class.getMethod(name));
    }

    private LoginUser loginUser(List<String> scopes) {
        return new LoginUser().setId(1L).setUserType(UserTypeEnum.ADMIN.getValue()).setScopes(scopes);
    }

    private AdminUserDO user(int userType, boolean required) {
        return new AdminUserDO().setUserType(userType).setPasswordChangeRequired(required);
    }

}
