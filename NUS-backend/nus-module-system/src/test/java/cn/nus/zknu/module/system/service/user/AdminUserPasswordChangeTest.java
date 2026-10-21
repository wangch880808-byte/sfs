package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.service.oauth2.OAuth2TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserPasswordChangeTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AdminUserServiceImpl service;
    @Mock
    private UserDeptScopeService deptScopeService;

    @Mock
    private AdminUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OAuth2TokenService oauth2TokenService;

    @Test
    void selfChange_clearsFlagAndAllTokens() {
        when(userMapper.selectById(1L)).thenReturn(user(2));
        when(passwordEncoder.matches("oldPass1", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newPass2")).thenReturn("encoded-new");

        service.updateUserPassword(1L, request("oldPass1", "newPass2"));

        ArgumentCaptor<AdminUserDO> updated = ArgumentCaptor.forClass(AdminUserDO.class);
        verify(userMapper).updateById(updated.capture());
        assertEquals("encoded-new", updated.getValue().getPassword());
        assertFalse(updated.getValue().getPasswordChangeRequired());
        verify(oauth2TokenService).removeAccessToken(1L, UserTypeEnum.ADMIN.getValue());
    }

    @Test
    void selfChange_rejectsInvalidNewPasswords() {
        when(userMapper.selectById(1L)).thenReturn(user(2));
        when(passwordEncoder.matches("oldPass1", "encoded-old")).thenReturn(true);

        assertServiceException(() -> service.updateUserPassword(1L, request("oldPass1", "short1")),
                USER_NEW_PASSWORD_INVALID);
        assertServiceException(() -> service.updateUserPassword(1L, request("oldPass1", "Student1")),
                USER_NEW_PASSWORD_SAME_AS_USERNAME);
        assertServiceException(() -> service.updateUserPassword(1L, request("oldPass1", "oldPass1")),
                USER_NEW_PASSWORD_SAME_AS_OLD);
        verify(userMapper, never()).updateById(any(AdminUserDO.class));
        verifyNoInteractions(oauth2TokenService);
    }

    @Test
    void adminReset_marksOnlyStudentsRequired() {
        when(userMapper.selectById(1L)).thenReturn(user(2));
        when(userMapper.selectById(2L)).thenReturn(user(1));
        when(passwordEncoder.encode("resetPass1")).thenReturn("encoded-reset");

        service.updateUserPassword(1L, "resetPass1");
        service.updateUserPassword(2L, "resetPass1");

        ArgumentCaptor<AdminUserDO> updated = ArgumentCaptor.forClass(AdminUserDO.class);
        verify(userMapper, times(2)).updateById(updated.capture());
        assertTrue(updated.getAllValues().get(0).getPasswordChangeRequired());
        assertNull(updated.getAllValues().get(1).getPasswordChangeRequired());
    }

    private AdminUserDO user(int type) {
        return new AdminUserDO().setId(1L).setUserType(type).setUsername("Student1")
                .setPassword("encoded-old");
    }

    private UserProfileUpdatePasswordReqVO request(String oldPassword, String newPassword) {
        UserProfileUpdatePasswordReqVO request = new UserProfileUpdatePasswordReqVO();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }

}
