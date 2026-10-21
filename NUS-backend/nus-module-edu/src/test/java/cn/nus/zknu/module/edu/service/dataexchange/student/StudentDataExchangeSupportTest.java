package cn.nus.zknu.module.edu.service.dataexchange.student;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.edu.service.classstudent.EduClassStudentService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.dal.mysql.permission.RoleMapper;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class StudentDataExchangeSupportTest extends BaseMockitoUnitTest {

    @Mock
    private EduDeptScopeService deptScopeService;
    @Mock private EduDataScopeService dataScopeService;
    @Mock private SecurityFrameworkService securityFrameworkService;

    @Mock
    private AdminUserService adminUserService;
    @Mock
    private UserProfileExtService profileService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private DeptService deptService;
    @Mock
    private EduClassStudentService classStudentService;
    @Mock
    private EduRoleApi eduRoleApi;

    private StudentDataExchangeSupport support;

    @BeforeEach
    void setUp() {
        lenient().when(deptScopeService.isUsernameInScope(any())).thenReturn(true);
        support = new StudentDataExchangeSupport();
        ReflectionTestUtils.setField(support, "adminUserService", adminUserService);
        ReflectionTestUtils.setField(support, "profileService", profileService);
        ReflectionTestUtils.setField(support, "permissionService", permissionService);
        ReflectionTestUtils.setField(support, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(support, "deptService", deptService);
        ReflectionTestUtils.setField(support, "classStudentService", classStudentService);
        ReflectionTestUtils.setField(support, "eduRoleApi", eduRoleApi);
        ReflectionTestUtils.setField(support, "deptScopeService", deptScopeService);
        ReflectionTestUtils.setField(support, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(support, "securityFrameworkService", securityFrameworkService);
        lenient().when(eduRoleApi.getTeacherRoleCode()).thenReturn("teacher");
        lenient().when(eduRoleApi.getStudentRoleCode()).thenReturn("student");
    }

    @Test
    void saveStudent_preservesExistingClassIds() {
        AdminUserDO existing = new AdminUserDO();
        existing.setId(1L);
        existing.setUsername("00001234");
        existing.setUserType(2);
        existing.setStatus(0);
        UserProfileDO profile = new UserProfileDO();
        profile.setUserId(1L);
        profile.setClassIds(new LinkedHashSet<>(Set.of(7L, 8L)));
        DeptDO major = new DeptDO();
        major.setId(9L);
        major.setDeptCode("M001");
        when(adminUserService.getUserByUsername("00001234")).thenReturn(existing);
        when(profileService.getUserProfileByUserId(1L)).thenReturn(profile);
        when(deptService.getDeptByCode("M001")).thenReturn(major);
        when(permissionService.getUserRoleIdListByUserId(1L)).thenReturn(Set.of(200L));
        when(roleMapper.selectByCode("teacher")).thenReturn(role(161L, "teacher"));
        when(roleMapper.selectByCode("student")).thenReturn(role(200L, "student"));

        StudentAccountImportRow row = new StudentAccountImportRow();
        row.setUsername("00001234");
        row.setNickname("张三");
        row.setSex(1);
        row.setMajorCode("M001");
        row.setGradeYear(2026);
        row.setStatus(0);
        support.saveStudent(row);

        ArgumentCaptor<UserSaveReqVO> captor = ArgumentCaptor.forClass(UserSaveReqVO.class);
        verify(adminUserService).updateUser(captor.capture());
        assertEquals(Set.of(7L, 8L), captor.getValue().getProfile().getClassIds());
        verify(classStudentService, never()).removeStudents(anyLong(), anyCollection());
    }

    @Test
    void validateNoIdentityConflict_rejectsExistingNonStudent() {
        AdminUserDO existing = new AdminUserDO();
        existing.setUserType(1);
        List<String> errors = new ArrayList<>();

        support.validateNoIdentityConflict(existing, errors);

        assertTrue(errors.stream().anyMatch(value -> value.contains("非学生账号")));
    }

    @Test
    void saveStudent_newStudentUsesDedicatedEntryAndOverridePassword() {
        when(roleMapper.selectByCode("teacher")).thenReturn(role(161L, "teacher"));
        when(roleMapper.selectByCode("student")).thenReturn(role(200L, "student"));
        when(adminUserService.createStudentUser(any())).thenReturn(2L);
        StudentAccountImportRow row = new StudentAccountImportRow();
        row.setUsername("20260001");
        row.setNickname("张三");
        row.setSex(0);
        row.setMajorCode("M001");
        row.setGradeYear(2026);
        row.setStatus(0);
        DeptDO major = new DeptDO();
        major.setId(9L);
        major.setDeptCode("M001");
        when(deptService.getDeptByCode("M001")).thenReturn(major);

        support.saveStudent(row, "newPass1");

        ArgumentCaptor<UserSaveReqVO> captor = ArgumentCaptor.forClass(UserSaveReqVO.class);
        verify(adminUserService).createStudentUser(captor.capture());
        assertEquals("newPass1", captor.getValue().getPassword());
        assertEquals(9L, captor.getValue().getDeptId());
        assertEquals(2026, captor.getValue().getProfile().getGradeYear());
    }

    @Test
    void scopedAdminCanImportAndExportStudentsWithMenuPermissions() {
        when(deptScopeService.isDeptScopedAdmin()).thenReturn(true);
        when(securityFrameworkService.hasPermission("system:user:create")).thenReturn(true);
        when(securityFrameworkService.hasPermission("system:user:update")).thenReturn(true);
        when(securityFrameworkService.hasPermission("system:user:query")).thenReturn(true);
        support.validateMasterPermission();
        support.validateMasterReadPermission();
    }

    @Test
    void globalAdminKeepsImportAndExportPermissions() {
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
        when(securityFrameworkService.hasPermission("system:user:create")).thenReturn(true);
        when(securityFrameworkService.hasPermission("system:user:update")).thenReturn(true);
        when(securityFrameworkService.hasPermission("system:user:query")).thenReturn(true);
        support.validateMasterPermission();
        support.validateMasterReadPermission();
        verify(deptScopeService, never()).isDeptScopedAdmin();
    }

    @Test
    void scopedRosterUsesDepartmentScopeWithoutChangingSharedClassAccess() {
        when(deptScopeService.isDeptScopedAdmin()).thenReturn(true);
        when(securityFrameworkService.hasPermission("edu:class:update")).thenReturn(true);
        support.validateRosterPermission(7L);
        verify(deptScopeService).validateClassInScope(7L);
        verify(dataScopeService, never()).validateClassAccess(anyLong());
    }

    @Test
    void rosterWriteAndReadUseSeparateClassScopes() {
        when(securityFrameworkService.hasPermission("edu:class:update")).thenReturn(true);
        when(securityFrameworkService.hasPermission("edu:class:query")).thenReturn(true);
        support.validateRosterPermission(7L);
        support.validateRosterReadPermission(7L);
        verify(dataScopeService).validateClassWriteAccess(7L);
        verify(dataScopeService).validateClassAccess(7L);
    }

    private RoleDO role(Long id, String code) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        return role;
    }

}
