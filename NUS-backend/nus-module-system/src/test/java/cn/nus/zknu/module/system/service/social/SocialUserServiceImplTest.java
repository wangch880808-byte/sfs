package cn.nus.zknu.module.system.service.social;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialUserBindDO;
import cn.nus.zknu.module.system.dal.dataobject.social.SocialUserDO;
import cn.nus.zknu.module.system.dal.mysql.social.SocialUserBindMapper;
import cn.nus.zknu.module.system.dal.mysql.social.SocialUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.SOCIAL_USER_BIND_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SocialUserServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private SocialUserMapper socialUserMapper;
    @Mock
    private SocialUserBindMapper socialUserBindMapper;

    private SocialUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SocialUserServiceImpl();
        ReflectionTestUtils.setField(service, "socialUserMapper", socialUserMapper);
        ReflectionTestUtils.setField(service, "socialUserBindMapper", socialUserBindMapper);
    }

    @Test
    void bindSocialIdentity_createsIdentityAndBinding() {
        SocialUserDO identity = SocialUserDO.builder().type(50).openid("openid").build();
        doAnswer(invocation -> {
            invocation.<SocialUserDO>getArgument(0).setId(10L);
            return 1;
        }).when(socialUserMapper).insert(identity);

        service.bindSocialIdentity(20L, 2, identity);

        verify(socialUserMapper).insert(identity);
        ArgumentCaptor<SocialUserBindDO> captor = ArgumentCaptor.forClass(SocialUserBindDO.class);
        verify(socialUserBindMapper).insert(captor.capture());
        SocialUserBindDO binding = captor.getValue();
        assertEquals(20L, binding.getUserId());
        assertEquals(10L, binding.getSocialUserId());
        assertEquals(50, binding.getSocialType());
    }

    @Test
    void bindSocialIdentity_rejectsIdentityOwnedByAnotherUser() {
        SocialUserDO identity = SocialUserDO.builder().id(10L).type(50).openid("openid").build();
        when(socialUserMapper.selectByTypeAndOpenid(50, "openid")).thenReturn(identity);
        when(socialUserBindMapper.selectByUserTypeAndSocialUserId(2, 10L))
                .thenReturn(SocialUserBindDO.builder().userId(99L).socialUserId(10L).build());

        assertServiceException(() -> service.bindSocialIdentity(20L, 2, identity), SOCIAL_USER_BIND_CONFLICT);

        verify(socialUserBindMapper, never()).insert(any(SocialUserBindDO.class));
    }

}
