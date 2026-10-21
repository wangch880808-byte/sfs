package cn.nus.zknu.module.system.controller.admin.auth;

import cn.nus.zknu.framework.ratelimiter.core.annotation.RateLimiter;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.notice.NoticeController;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.convert.auth.AuthConvert;
import cn.nus.zknu.module.system.service.social.SocialClientService;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class AuthControllerTest extends BaseMockitoUnitTest {

    @Mock
    private SocialClientService socialClientService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "socialClientService", socialClientService);
    }

    @Test
    void getSocialLoginConfig_isAnonymousRateLimitedAndReturnsBooleanOnly() throws Exception {
        Method method = AuthController.class.getMethod("getSocialLoginConfig");
        assertThat(method.isAnnotationPresent(PermitAll.class)).isTrue();
        assertThat(method.isAnnotationPresent(RateLimiter.class)).isTrue();
        assertThat(mappedPaths(AuthController.class)).contains("/social-login-config");

        when(socialClientService.isDingtalkEnabled()).thenReturn(true, false);
        assertThat(controller.getSocialLoginConfig().getData().dingtalkEnabled()).isTrue();
        assertThat(controller.getSocialLoginConfig().getData().dingtalkEnabled()).isFalse();
        assertThat(controller.getSocialLoginConfig().getData().getClass().getRecordComponents())
                .extracting(component -> component.getName()).containsExactly("dingtalkEnabled");
    }

    @Test
    void removedAnonymousEndpointsAreNotMapped() {
        assertThat(mappedPaths(AuthController.class))
                .doesNotContain("/register", "/sms-login", "/send-sms-code", "/reset-password", "/login-features");
        assertThat(mappedPaths(NoticeController.class)).doesNotContain("/push");
    }

    @Test
    void removedControllersAndRequestObjectsNoLongerExist() {
        List<String> removedClasses = List.of(
                "cn.nus.zknu.module.system.controller.admin.oauth2.OAuth2OpenController",
                "cn.nus.zknu.module.system.controller.admin.oauth2.OAuth2UserController",
                "cn.nus.zknu.module.system.convert.oauth2.OAuth2OpenConvert",
                "cn.nus.zknu.module.system.controller.admin.sms.SmsCallbackController",
                "cn.nus.zknu.module.system.controller.app.dict.AppDictDataController",
                "cn.nus.zknu.module.system.controller.app.ip.AppAreaController",
                "cn.nus.zknu.module.system.controller.admin.auth.vo.AuthRegisterReqVO",
                "cn.nus.zknu.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO",
                "cn.nus.zknu.module.system.controller.admin.auth.vo.AuthSmsSendReqVO",
                "cn.nus.zknu.module.system.controller.admin.auth.vo.AuthResetPasswordReqVO");
        removedClasses.forEach(className -> assertThatThrownBy(() -> Class.forName(className))
                .as(className).isInstanceOf(ClassNotFoundException.class));
    }

    @Test
    void permissionInfo_containsPasswordChangeRequired() {
        AdminUserDO student = new AdminUserDO().setId(1L).setUserType(2).setPasswordChangeRequired(true);

        assertThat(AuthConvert.INSTANCE.convert(student, List.of(), List.of()).isPasswordChangeRequired()).isTrue();
    }

    static List<String> mappedPaths(Class<?> controllerClass) {
        return Arrays.stream(controllerClass.getDeclaredMethods())
                .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.path()))
                .toList();
    }

}
