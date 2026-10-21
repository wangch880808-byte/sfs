package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.USER_EMAIL_EXISTS;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.USER_MOBILE_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 邮箱、手机号唯一：没有改动时直接放行（历史上的重复数据不影响保存），改成别人在用的值时提示占用的账号。
 */
class AdminUserContactUniqueTest extends BaseMockitoUnitTest {

    @Mock private AdminUserMapper userMapper;
    @Mock private UserDeptScopeService deptScopeService;
    private AdminUserServiceImpl service;

    private final AdminUserDO self = new AdminUserDO().setId(1L).setUsername("20011030").setNickname("徐艳伟")
            .setEmail("dup-user@example.com").setMobile("13800001111").setStatus(0);
    private final AdminUserDO other = new AdminUserDO().setId(2L).setUsername("20011031").setNickname("王老师")
            .setEmail("dup-user@example.com").setMobile("13800001111").setStatus(1);

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "deptScopeService", deptScopeService);
        lenient().when(userMapper.selectById(1L)).thenReturn(self);
    }

    @Test
    void unchangedEmailAndMobileSaveEvenWhenHistoricalDuplicatesExist() {
        lenient().when(userMapper.selectListByEmail(anyString())).thenReturn(List.of(self, other));
        lenient().when(userMapper.selectListByMobile(anyString())).thenReturn(List.of(self, other));

        assertDoesNotThrow(() -> service.validateEmailUnique(1L, "dup-user@EXAMPLE.com"));
        assertDoesNotThrow(() -> service.validateMobileUnique(1L, "13800001111"));
        verify(userMapper, never()).selectListByEmail(anyString());
        verify(userMapper, never()).selectListByMobile(anyString());
    }

    @Test
    void changingToAnotherAccountsEmailNamesTheOwnerAndMarksDisabled() {
        when(userMapper.selectListByEmail("taken@qq.com")).thenReturn(List.of(other));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateEmailUnique(1L, "taken@qq.com"));
        assertEquals(USER_EMAIL_EXISTS.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("20011031") && ex.getMessage().contains("王老师，已停用"), ex.getMessage());
    }

    @Test
    void changingToAnotherAccountsMobileNamesTheOwner() {
        when(userMapper.selectListByMobile("13900000000")).thenReturn(List.of(other.setStatus(0)));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateMobileUnique(1L, "13900000000"));
        assertEquals(USER_MOBILE_EXISTS.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("20011031") && !ex.getMessage().contains("已停用"), ex.getMessage());
    }

    @Test
    void newAccountCannotReuseAnExistingEmail() {
        when(userMapper.selectListByEmail("dup-user@example.com")).thenReturn(List.of(self));
        assertThrows(ServiceException.class, () -> service.validateEmailUnique(null, "dup-user@example.com"));
    }

    @Test
    void profileUpdateWithUnchangedDuplicatedEmailSucceeds() {
        UserProfileUpdateReqVO req = new UserProfileUpdateReqVO();
        req.setNickname("徐艳伟");
        req.setEmail("dup-user@example.com");
        req.setMobile("13800001111");
        lenient().when(userMapper.selectListByEmail(anyString())).thenReturn(List.of(self, other));

        assertDoesNotThrow(() -> service.updateUserProfile(1L, req));
        verify(userMapper).updateById(any(AdminUserDO.class));
    }
}
