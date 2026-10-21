package cn.nus.zknu.module.edu.service.dataexchange.user;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.permission.RoleMapper;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserAccountDataExchangeSupportTest extends BaseMockitoUnitTest {

    @Mock
    private EduDeptScopeService deptScopeService;

    @Mock
    private PermissionService permissionService;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private AdminUserService adminUserService;
    @Mock
    private DeptService deptService;
    @Mock
    private ConfigApi configApi;
    @Mock
    private UserProfileExtService profileService;
    @Mock
    private EduRoleApi eduRoleApi;

    private UserAccountDataExchangeSupport support;

    @BeforeEach
    void setUp() {
        lenient().when(deptScopeService.isUsernameInScope(any())).thenReturn(true);
        support = new UserAccountDataExchangeSupport();
        ReflectionTestUtils.setField(support, "permissionService", permissionService);
        ReflectionTestUtils.setField(support, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(support, "adminUserService", adminUserService);
        ReflectionTestUtils.setField(support, "deptService", deptService);
        ReflectionTestUtils.setField(support, "configApi", configApi);
        ReflectionTestUtils.setField(support, "profileService", profileService);
        ReflectionTestUtils.setField(support, "eduRoleApi", eduRoleApi);
        ReflectionTestUtils.setField(support, "deptScopeService", deptScopeService);
        lenient().when(eduRoleApi.getTeacherRoleCode()).thenReturn("teacher");
        lenient().when(eduRoleApi.getStudentRoleCode()).thenReturn("student");
    }

    @Test
    void expectedRoleIds_replacesConflictingIdentityAndPreservesBusinessRoles() {
        AdminUserDO existing = new AdminUserDO();
        existing.setId(7L);
        when(permissionService.getUserRoleIdListByUserId(7L)).thenReturn(Set.of(200L, 999L));
        when(roleMapper.selectByCode("teacher")).thenReturn(role(161L, "teacher"));
        when(roleMapper.selectByCode("student")).thenReturn(role(200L, "student"));

        Set<Long> roles = support.expectedRoleIds(existing, "teacher");

        assertEquals(Set.of(161L, 999L), roles);
    }

    @Test
    void expectedRoleIds_usesConfiguredIdentityRoleCodes() {
        when(eduRoleApi.getTeacherRoleCode()).thenReturn("js");
        when(eduRoleApi.getStudentRoleCode()).thenReturn("xs");
        AdminUserDO existing = new AdminUserDO();
        existing.setId(7L);
        when(permissionService.getUserRoleIdListByUserId(7L)).thenReturn(Set.of(301L, 999L));
        when(roleMapper.selectByCode("js")).thenReturn(role(300L, "js"));
        when(roleMapper.selectByCode("xs")).thenReturn(role(301L, "xs"));

        assertEquals(Set.of(300L, 999L), support.expectedRoleIds(existing, support.getTeacherRoleCode()));
        verify(roleMapper, never()).selectByCode("teacher");
    }

    @Test
    void toProfile_newTeacherUsesNonStudentGradeYear() {
        UserAccountImportRow row = new UserAccountImportRow();
        row.setProfessionalTitle(2);

        UserProfileExtReqVO profile = ReflectionTestUtils.invokeMethod(support, "toProfile", row, null);

        assertEquals(2, profile.getProfessionalTitle());
        assertEquals(0, profile.getGradeYear());
    }

    @Test
    void save_newTeacherAllowsNoDepartmentAndUsesOverridePassword() {
        when(roleMapper.selectByCode("teacher")).thenReturn(role(161L, "teacher"));
        when(roleMapper.selectByCode("student")).thenReturn(role(200L, "student"));
        when(adminUserService.createUser(any())).thenReturn(8L);
        UserAccountImportRow row = new UserAccountImportRow();
        row.setUsername("T20260001");
        row.setNickname("张老师");
        row.setSex(0);
        row.setStatus(0);

        support.save(row, UserAccountDataExchangeSupport.TEACHER_USER_TYPE, "teacher", "newPass1");

        ArgumentCaptor<UserSaveReqVO> captor = ArgumentCaptor.forClass(UserSaveReqVO.class);
        verify(adminUserService).createUser(captor.capture());
        assertEquals("newPass1", captor.getValue().getPassword());
        assertEquals(null, captor.getValue().getDeptId());
        verify(configApi, never()).getConfigValueByKey(anyString());
    }

    @Test
    void save_existingTeacherNeverChangesPassword() {
        AdminUserDO existing = new AdminUserDO();
        existing.setId(8L);
        existing.setUsername("T20260001");
        existing.setStatus(0);
        when(adminUserService.getUserByUsername("T20260001")).thenReturn(existing);
        when(permissionService.getUserRoleIdListByUserId(8L)).thenReturn(Set.of(161L));
        when(roleMapper.selectByCode("teacher")).thenReturn(role(161L, "teacher"));
        when(roleMapper.selectByCode("student")).thenReturn(role(200L, "student"));
        UserAccountImportRow row = new UserAccountImportRow();
        row.setUsername("T20260001");
        row.setNickname("张老师");
        row.setSex(0);
        row.setStatus(0);

        support.save(row, UserAccountDataExchangeSupport.TEACHER_USER_TYPE, "teacher", "newPass1");

        ArgumentCaptor<UserSaveReqVO> captor = ArgumentCaptor.forClass(UserSaveReqVO.class);
        verify(adminUserService).updateUser(captor.capture());
        assertEquals(null, captor.getValue().getPassword());
    }

    private RoleDO role(Long id, String code) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        return role;
    }


    @Test
    void conflictingContactOwner_unchangedValuesAreNeverConflicts() {
        AdminUserDO existing = new AdminUserDO().setId(1L).setEmail("dup-user@example.com").setMobile("13800001111");

        assertEquals(null, support.getConflictingEmailOwner("dup-user@EXAMPLE.com", existing));
        assertEquals(null, support.getConflictingMobileOwner("13800001111", existing));
        verify(adminUserService, never()).getOtherUserByEmail(any(), any());
        verify(adminUserService, never()).getOtherUserByMobile(any(), any());
    }

    @Test
    void conflictingContactOwner_changedValueLooksForOtherAccounts() {
        AdminUserDO existing = new AdminUserDO().setId(1L).setEmail("old@qq.com");
        AdminUserDO other = new AdminUserDO().setId(2L).setUsername("20011031");
        when(adminUserService.getOtherUserByEmail("taken@qq.com", 1L)).thenReturn(other);

        assertEquals(other, support.getConflictingEmailOwner("taken@qq.com", existing));
        assertEquals(null, support.getConflictingEmailOwner(null, existing));
    }
}
