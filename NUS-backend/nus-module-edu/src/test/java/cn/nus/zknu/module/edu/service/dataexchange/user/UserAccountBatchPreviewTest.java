package cn.nus.zknu.module.edu.service.dataexchange.user;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptImportLookupDO;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO;
import cn.nus.zknu.module.system.dal.mysql.dept.DeptMapper;
import cn.nus.zknu.module.system.dal.mysql.permission.RoleMapper;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 support + mock mapper，验证两个账号模板均使用批量查询且角色仅按编码查一次。 */
class UserAccountBatchPreviewTest extends BaseMockitoUnitTest {
    @Mock private AdminUserMapper users;
    @Mock private DeptMapper depts;
    @Mock private RoleMapper roles;
    @Mock private DeptService deptService;
    @Mock private UserProfileExtService profiles;
    @Mock private PermissionService permissions;
    @Mock private EduRoleApi roleApi;
    @Mock private EduDeptScopeService scope;
    @Mock private AdminUserService userService;
    private UserAccountDataExchangeSupport support;

    @BeforeEach
    void setUp() {
        support = new UserAccountDataExchangeSupport();
        ReflectionTestUtils.setField(support, "previewUserMapper", users);
        ReflectionTestUtils.setField(support, "previewDeptMapper", depts);
        ReflectionTestUtils.setField(support, "roleMapper", roles);
        ReflectionTestUtils.setField(support, "deptService", deptService);
        ReflectionTestUtils.setField(support, "profileService", profiles);
        ReflectionTestUtils.setField(support, "permissionService", permissions);
        ReflectionTestUtils.setField(support, "eduRoleApi", roleApi);
        ReflectionTestUtils.setField(support, "adminUserService", userService);
    }

    @Test
    void teacherFiftyRowsLoadEachRoleCodeOnce() throws Exception {
        verifyFifty(new TeacherMasterDataExchangeHandler(), 1, "教师工号*");
    }

    @Test
    void systemFiftyRowsLoadEachRoleCodeOnce() throws Exception {
        verifyFifty(new SystemUserDataExchangeHandler(), 0, "登录账号*");
    }

    private void verifyFifty(AbstractUserAccountDataExchangeHandler handler, int type, String accountHeader) throws Exception {
        ReflectionTestUtils.setField(handler, "support", support);
        ReflectionTestUtils.setField(handler, "deptScopeService", scope);
        when(scope.isLoadedUsernameInScope(any())).thenReturn(true);
        when(scope.isLoadedDeptCodeInScope(any())).thenReturn(true);
        when(roleApi.getTeacherRoleCode()).thenReturn("teacher");
        when(roleApi.getStudentRoleCode()).thenReturn("student");
        when(roles.selectByCode("teacher")).thenReturn(new RoleDO().setId(161L));
        when(roles.selectByCode("student")).thenReturn(new RoleDO().setId(200L));
        List<UserImportLookupDO> existing = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UserImportLookupDO user = new UserImportLookupDO();
            user.setId(100L + i); user.setUsername("U2026" + i); user.setLookupKey(user.getUsername());
            user.setNickname("用户" + i); user.setDeptId(10L); user.setUserType(type); user.setSex(0); user.setStatus(0);
            existing.add(user);
        }
        when(users.selectImportByUsernames(any())).thenReturn(existing);
        when(permissions.getUserRoleIdListByUserId(anyLong())).thenReturn(type == 1 ? Set.of(161L, 999L) : Set.of(999L));
        DeptDO dept = new DeptDO(); dept.setId(10L); dept.setDeptCode("CS");
        when(deptService.getDeptList(anyCollection())).thenReturn(List.of(dept));
        DeptImportLookupDO lookup = new DeptImportLookupDO();
        lookup.setId(10L); lookup.setDeptCode("CS"); lookup.setLookupKey("CS");
        when(depts.selectImportByCodes(anyMap())).thenReturn(List.of(lookup));
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("账号数据");
            List<String> headers = new ArrayList<>(List.of(accountHeader, "姓名*", "部门编码", "邮箱", "手机号", "性别", "状态"));
            if (type == 1) headers.add("职称");
            write(sheet.createRow(0), headers);
            for (int i = 0; i < 50; i++) write(sheet.createRow(i + 1), List.of("U2026" + i, "用户" + i, "CS", "", "", "", ""));
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
        var result = handler.preview(file, "{\"updateSupport\":true}");
        assertEquals(50, result.size());
        assertTrue(result.stream().allMatch(row -> row.getErrors().isEmpty()), result.toString());
        assertTrue(result.stream().allMatch(row -> row.getAction().name().equals("SKIP")));
        verify(users, times(2)).selectImportByUsernames(argThat(names -> names.size() == 50));
        verify(depts, times(2)).selectImportByCodes(Map.of("CS", "CS"));
        verify(profiles).getUserProfileMap(argThat(ids -> ids.size() == 50));
        verify(roles).selectByCode("teacher");
        verify(roles).selectByCode("student");
        verify(permissions, times(50)).getUserRoleIdListByUserId(anyLong());
        verifyNoInteractions(userService);
        verify(deptService, never()).getDept(any());
        verify(deptService, never()).getDeptByCode(any());
        verify(profiles, never()).getUserProfileByUserId(any());
    }

    @Test
    void departmentBatchKeepsServiceCodeNormalization() {
        support.getDeptsByCodes(List.of("\tcs\t", "mixed"));
        verify(depts).selectImportByCodes(Map.of("\tcs\t", "CS", "mixed", "MIXED"));
    }

    private void write(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
    }
}
