package cn.nus.zknu.module.system.framework.wechat;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialClientDO;
import cn.nus.zknu.module.system.service.social.SocialClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class WechatMiniProgramClientTest extends BaseMockitoUnitTest {

    @Mock
    private SocialClientService socialClientService;

    private WechatMiniProgramClient client;

    @BeforeEach
    void setUp() {
        client = new WechatMiniProgramClient();
        ReflectionTestUtils.setField(client, "socialClientService", socialClientService);
    }

    @Test
    void isConfigured_usesEnabledAdminSocialClient() {
        SocialClientDO socialClient = SocialClientDO.builder()
                .clientId("app-id")
                .clientSecret("app-secret")
                .status(CommonStatusEnum.ENABLE.getStatus())
                .build();
        when(socialClientService.getSocialClient(50, 2)).thenReturn(socialClient);

        assertTrue(client.isConfigured());

        socialClient.setStatus(CommonStatusEnum.DISABLE.getStatus());
        assertFalse(client.isConfigured());
    }

}
