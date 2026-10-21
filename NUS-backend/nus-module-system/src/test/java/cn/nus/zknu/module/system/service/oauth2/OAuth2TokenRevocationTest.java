package cn.nus.zknu.module.system.service.oauth2;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.nus.zknu.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.nus.zknu.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.nus.zknu.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.nus.zknu.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.Mockito.*;

class OAuth2TokenRevocationTest extends BaseMockitoUnitTest {

    @InjectMocks
    private OAuth2TokenServiceImpl service;
    @Mock
    private OAuth2AccessTokenMapper accessTokenMapper;
    @Mock
    private OAuth2RefreshTokenMapper refreshTokenMapper;
    @Mock
    private OAuth2AccessTokenRedisDAO accessTokenRedisDAO;

    @Test
    void removeByUser_clearsAccessRefreshAndRefreshAsAccessCache() {
        when(accessTokenMapper.selectListByUserIdAndUserType(1L, 1)).thenReturn(List.of(
                new OAuth2AccessTokenDO().setId(8L).setAccessToken("access").setRefreshToken("refresh")));
        when(refreshTokenMapper.selectListByUserIdAndUserType(1L, 1)).thenReturn(List.of(
                new OAuth2RefreshTokenDO().setRefreshToken("refresh"),
                new OAuth2RefreshTokenDO().setRefreshToken("orphan-refresh")));

        service.removeAccessToken(1L, 1);

        verify(accessTokenMapper).deleteById(8L);
        verify(accessTokenRedisDAO).delete("access");
        verify(accessTokenRedisDAO).delete("refresh");
        verify(accessTokenRedisDAO).delete("orphan-refresh");
        verify(refreshTokenMapper).deleteByUserIdAndUserType(1L, 1);
    }

}
