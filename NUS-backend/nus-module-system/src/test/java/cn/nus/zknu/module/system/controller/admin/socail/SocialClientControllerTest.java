package cn.nus.zknu.module.system.controller.admin.socail;

import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialClientDO;
import cn.nus.zknu.module.system.service.social.SocialClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SocialClientControllerTest extends BaseMockitoUnitTest {

    @Mock
    private SocialClientService socialClientService;

    private SocialClientController controller;

    @BeforeEach
    void setUp() {
        controller = new SocialClientController();
        ReflectionTestUtils.setField(controller, "socialClientService", socialClientService);
    }

    @Test
    void getSocialClient_neverReturnsClientSecret() {
        when(socialClientService.getSocialClient(1L)).thenReturn(SocialClientDO.builder()
                .id(1L)
                .clientId("app-id")
                .clientSecret("must-not-leak")
                .build());

        String json = JsonUtils.toJsonString(controller.getSocialClient(1L));

        assertThat(json).contains("\"clientSecretConfigured\":true");
        assertThat(json).doesNotContain("must-not-leak", "\"clientSecret\":");
    }

}
