package cn.nus.zknu.module.system.service.auth;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.nus.zknu.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.logger.LoginLogService;
import cn.nus.zknu.module.system.service.oauth2.OAuth2TokenService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AdminAuthPasswordChangeTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AdminAuthServiceImpl service;
    @Mock
    private AdminUserService userService;
    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private LoginLogService loginLogService;

    @Test
    void passwordLoginAndRefresh_exposePasswordChangeRequired() {
        service.setCaptchaEnable(false);
        AdminUserDO student = new AdminUserDO().setId(1L).setUsername("Student1")
                .setPassword("encoded").setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setUserType(2).setPasswordChangeRequired(true);
        when(userService.getUserByUsername("Student1")).thenReturn(student);
        when(userService.isPasswordMatch("oldPass1", "encoded")).thenReturn(true);
        when(userService.getUser(1L)).thenReturn(student);
        when(oauth2TokenService.createAccessToken(eq(1L), eq(UserTypeEnum.ADMIN.getValue()), anyString(), isNull()))
                .thenReturn(new OAuth2AccessTokenDO().setUserId(1L));
        when(oauth2TokenService.refreshAccessToken(anyString(), anyString()))
                .thenReturn(new OAuth2AccessTokenDO().setUserId(1L));

        assertTrue(service.login(AuthLoginReqVO.builder().username("Student1").password("oldPass1").build())
                .isPasswordChangeRequired());
        assertTrue(service.refreshToken("refresh").isPasswordChangeRequired());
    }

}
