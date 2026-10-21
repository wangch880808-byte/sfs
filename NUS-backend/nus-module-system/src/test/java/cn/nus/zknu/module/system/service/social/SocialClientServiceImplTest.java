package cn.nus.zknu.module.system.service.social;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.module.system.controller.admin.socail.vo.client.SocialClientSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialClientDO;
import cn.nus.zknu.module.system.dal.mysql.social.SocialClientMapper;
import cn.nus.zknu.module.system.framework.justauth.core.AuthRequestFactory;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_AUTH_FLOW_UNSUPPORTED;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_DINGTALK_DISABLED;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_SECRET_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SocialClientServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private SocialClientMapper socialClientMapper;

    @Mock
    private AuthRequestFactory authRequestFactory;

    @InjectMocks
    private SocialClientServiceImpl service;

    @Test
    void updateSocialClient_blankSecretKeepsExistingSecret() {
        SocialClientDO existing = SocialClientDO.builder().id(1L).clientSecret("existing-secret").build();
        when(socialClientMapper.selectById(1L)).thenReturn(existing);
        when(socialClientMapper.selectBySocialTypeAndUserType(50, 2)).thenReturn(existing);

        SocialClientSaveReqVO request = new SocialClientSaveReqVO();
        request.setId(1L);
        request.setName("微信小程序");
        request.setSocialType(50);
        request.setUserType(2);
        request.setClientId("app-id");
        request.setClientSecret(" ");
        request.setStatus(0);

        service.updateSocialClient(request);

        ArgumentCaptor<SocialClientDO> captor = ArgumentCaptor.forClass(SocialClientDO.class);
        verify(socialClientMapper).updateById(captor.capture());
        assertEquals("existing-secret", captor.getValue().getClientSecret());
    }

    @Test
    void createSocialClient_blankSecretRejectedEvenWhenRequestContainsId() {
        SocialClientSaveReqVO request = new SocialClientSaveReqVO();
        request.setId(99L);
        request.setSocialType(50);
        request.setUserType(2);

        assertServiceException(() -> service.createSocialClient(request), SOCIAL_CLIENT_SECRET_REQUIRED);
        verify(socialClientMapper, never()).insert(any(SocialClientDO.class));
    }

    @Test
    void buildAuthRequest_wechatMiniProgramRejectsGenericOauthFlow() {
        assertServiceException(() -> service.buildAuthRequest(50, 2), SOCIAL_CLIENT_AUTH_FLOW_UNSUPPORTED);
    }

    @Test
    void dingtalk_completeConfigEnabledAndUsesDatabaseCredentials() {
        SocialClientDO client = dingtalkClient(CommonStatusEnum.ENABLE.getStatus(), "app-id", "app-secret");
        when(socialClientMapper.selectBySocialTypeAndUserType(20, UserTypeEnum.ADMIN.getValue())).thenReturn(client);
        AuthRequest request = mock(AuthRequest.class);
        when(authRequestFactory.createDingtalkRequest(any(AuthConfig.class))).thenReturn(request);

        assertTrue(service.isDingtalkEnabled());
        assertSame(request, service.buildAuthRequest(20, UserTypeEnum.ADMIN.getValue()));
        ArgumentCaptor<AuthConfig> config = ArgumentCaptor.forClass(AuthConfig.class);
        verify(authRequestFactory).createDingtalkRequest(config.capture());
        assertEquals("app-id", config.getValue().getClientId());
        assertEquals("app-secret", config.getValue().getClientSecret());
        assertTrue(config.getValue().isIgnoreCheckRedirectUri());
    }

    @Test
    void dingtalk_missingConfigDisabled() {
        assertDingtalkDisabled(null);
    }

    @Test
    void dingtalk_stoppedConfigDisabled() {
        assertDingtalkDisabled(dingtalkClient(CommonStatusEnum.DISABLE.getStatus(), "app-id", "app-secret"));
    }

    @Test
    void dingtalk_missingSecretDisabled() {
        assertDingtalkDisabled(dingtalkClient(CommonStatusEnum.ENABLE.getStatus(), "app-id", " "));
    }

    @Test
    void dingtalk_missingClientIdDisabled() {
        assertDingtalkDisabled(dingtalkClient(CommonStatusEnum.ENABLE.getStatus(), " ", "app-secret"));
    }

    private void assertDingtalkDisabled(SocialClientDO client) {
        when(socialClientMapper.selectBySocialTypeAndUserType(20, UserTypeEnum.ADMIN.getValue())).thenReturn(client);
        assertFalse(service.isDingtalkEnabled());
        assertServiceException(() -> service.buildAuthRequest(20, UserTypeEnum.ADMIN.getValue()),
                SOCIAL_CLIENT_DINGTALK_DISABLED);
        verify(authRequestFactory, never()).createDingtalkRequest(any());
    }

    private static SocialClientDO dingtalkClient(Integer status, String clientId, String clientSecret) {
        return SocialClientDO.builder().socialType(20).userType(UserTypeEnum.ADMIN.getValue())
                .status(status).clientId(clientId).clientSecret(clientSecret).build();
    }

}
