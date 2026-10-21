package cn.nus.zknu.module.edu.service.dataexchange.user;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeacherMasterDataExchangeHandlerTest extends BaseMockitoUnitTest {

    @Mock
    private EduDeptScopeService deptScopeService;

    @Mock
    private UserAccountDataExchangeSupport support;

    private TeacherMasterDataExchangeHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(deptScopeService.isDeptCodeInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isUsernameInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isClassInScope(any())).thenReturn(true);
        lenient().doCallRealMethod().when(deptScopeService).markOutOfScope(any());
        lenient().doCallRealMethod().when(deptScopeService).markSkipped(any(), any());
        lenient().when(support.getUsersByUsernames(any())).thenAnswer(inv -> {
            java.util.Map<String, AdminUserDO> users = new java.util.HashMap<>();
            for (String name : inv.<java.util.Collection<String>>getArgument(0)) {
                AdminUserDO user = support.getUser(name);
                if (user != null) users.put(name, user);
            }
            return users;
        });
        lenient().when(support.getDepts(any())).thenAnswer(inv -> inv.<java.util.Collection<Long>>getArgument(0)
                .stream().map(support::getDept).filter(java.util.Objects::nonNull).toList());
        lenient().when(support.getDeptsByCodes(any())).thenAnswer(inv -> {
            java.util.Map<String, DeptDO> depts = new java.util.HashMap<>();
            for (String code : inv.<java.util.Collection<String>>getArgument(0)) {
                DeptDO dept = support.getDept(code);
                if (dept != null) depts.put(code, dept);
            }
            return depts;
        });
        lenient().when(support.getScopeDeptsByCodes(any())).thenAnswer(inv -> {
            java.util.Map<String, DeptDO> depts = new java.util.HashMap<>();
            for (String code : inv.<java.util.Collection<String>>getArgument(0)) {
                DeptDO dept = new DeptDO(); dept.setDeptCode(code); depts.put(code, dept);
            }
            return depts;
        });
        lenient().when(deptScopeService.isLoadedDeptCodeInScope(any())).thenAnswer(inv -> {
            DeptDO dept = inv.getArgument(0);
            return deptScopeService.isDeptCodeInScope(dept == null ? null : dept.getDeptCode());
        });
        lenient().when(deptScopeService.isLoadedUsernameInScope(any())).thenReturn(true);
        lenient().when(support.expectedRoleIds(anySet(), any(), anyMap())).thenReturn(Set.of(161L));
        lenient().when(support.snapshot(any(AdminUserDO.class), any(), any(), anySet()))
                .thenAnswer(inv -> support.snapshot(inv.<AdminUserDO>getArgument(0)));
        handler = new TeacherMasterDataExchangeHandler();
        ReflectionTestUtils.setField(handler, "support", support);
        ReflectionTestUtils.setField(handler, "deptScopeService", deptScopeService);
        lenient().when(support.getTeacherTitleOptions())
                .thenReturn(List.of("助教（高校）", "讲师（高校）", "副教授", "教授"));
        lenient().when(support.expectedRoleIds(any(), any())).thenReturn(Set.of(161L));
        lenient().when(support.snapshot(any(UserAccountImportRow.class), any(Integer.class), any()))
                .thenAnswer(invocation -> java.util.Map.of("username",
                        invocation.<UserAccountImportRow>getArgument(0).getUsername()));
    }

    @Test
    void preview_skipsTeacherFromAnotherCollege() throws Exception {
        when(deptScopeService.isDeptCodeInScope("FOREIGN")).thenReturn(false);
        List<EduImportPreviewRow> rows = handler.preview(workbook(
                new String[]{"T20260001", "外院教师", "FOREIGN", "", "", "", "", ""}), "{}");
        assertEquals("SKIP", rows.get(0).getAction().name());
        assertTrue(rows.get(0).getErrors().isEmpty());
        assertEquals("不在本学院", JSONUtil.parseObj(rows.get(0).getNormalizedData()).getStr("skipReason"));
    }

    @Test
    void preview_newTeacherUsesFixedIdentityTemplate() throws Exception {
        DeptDO dept = dept("CS");
        lenient().when(support.getDept("CS")).thenReturn(dept);

        List<EduImportPreviewRow> rows = handler.preview(workbook(
                new String[]{"T20260001", "张老师", "CS", "teacher@example.edu.cn",
                        "13800138000", "男", "正常", "讲师"}), "{}");

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().isEmpty());
        assertEquals("INSERT", rows.get(0).getAction().name());
        UserAccountImportRow normalized = JSONUtil.toBean(rows.get(0).getNormalizedData(), UserAccountImportRow.class);
        assertEquals("T20260001", normalized.getUsername());
        assertEquals(2, normalized.getProfessionalTitle());
    }

    @Test
    void template_departmentDropdownSelectionImportsItsCode() throws Exception {
        DeptDO school = dept("ZKNU");
        school.setId(1L);
        school.setParentId(0L);
        school.setName("周口师范学院");
        school.setDeptType("SCHOOL");
        DeptDO dept = dept("CS");
        dept.setId(2L);
        dept.setParentId(1L);
        dept.setName("计算机学院");
        dept.setDeptType("COLLEGE");
        when(support.getActiveDepartments()).thenReturn(List.of(school, dept));
        lenient().when(support.getDept("CS")).thenReturn(dept);

        try (XSSFWorkbook template = EduXlsxWorkbookUtils.open(handler.generate())) {
            assertEquals("NUS_DROPDOWN_0", template.getSheet("账号数据").getDataValidations().stream()
                    .filter(validation -> validation.getRegions().getCellRangeAddresses()[0].getFirstColumn() == 2)
                    .findFirst().orElseThrow().getValidationConstraint().getFormula1());
            assertEquals("计算机学院 [CS]",
                    template.getSheet("下拉选项").getRow(1).getCell(0).getStringCellValue());
            assertEquals("NUS_DROPDOWN_1", template.getSheet("账号数据").getDataValidations().stream()
                    .filter(validation -> validation.getRegions().getCellRangeAddresses()[0].getFirstColumn() == 7)
                    .findFirst().orElseThrow().getValidationConstraint().getFormula1());
            assertEquals("助教（高校）",
                    template.getSheet("下拉选项").getRow(1).getCell(1).getStringCellValue());
        }
        List<EduImportPreviewRow> rows = handler.preview(workbook(
                new String[]{"T20260001", "张老师", "计算机学院 [CS]", "", "", "", "", ""}), "{}");

        UserAccountImportRow normalized = JSONUtil.toBean(rows.get(0).getNormalizedData(), UserAccountImportRow.class);
        assertEquals("CS", normalized.getDeptCode());
        assertTrue(rows.get(0).getErrors().isEmpty());
    }

    @Test
    void preview_onlyRequiredFieldsAllowsOptionalFieldsAndMissingDefaultPassword() throws Exception {
        List<EduImportPreviewRow> rows = handler.preview(workbook(
                new String[]{"T20260001", "张老师", "", "", "", "", "", ""}), "{}");

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().isEmpty());
    }

    @Test
    void preview_existingStudentCannotBeConvertedToTeacher() throws Exception {
        AdminUserDO student = new AdminUserDO();
        student.setId(9L);
        student.setUsername("S20260001");
        student.setUserType(UserAccountDataExchangeSupport.STUDENT_USER_TYPE);
        student.setDeptId(1L);
        student.setStatus(0);
        lenient().when(support.getUser("S20260001")).thenReturn(student);
        lenient().when(support.getDept("CS")).thenReturn(dept("CS"));
        lenient().when(support.getDept(1L)).thenReturn(dept("CS"));
        lenient().when(support.snapshot(student)).thenReturn(java.util.Map.of("username", "S20260001"));

        List<EduImportPreviewRow> rows = handler.preview(workbook(
                new String[]{"S20260001", "学生账号", "CS", "", "", "", "", ""}),
                "{\"updateSupport\":true}");

        assertTrue(rows.get(0).getErrors().stream().anyMatch(value -> value.contains("其他身份")));
    }

    @Test
    void preview_duplicateAccountsMarksEveryRowInvalid() throws Exception {
        lenient().when(support.getDept("CS")).thenReturn(dept("CS"));
        byte[] file;
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            write(workbook.getSheet("账号数据").createRow(1),
                    "T20260001", "张老师", "CS", "", "", "", "", "");
            write(workbook.getSheet("账号数据").createRow(2),
                    "T20260001", "李老师", "CS", "", "", "", "", "");
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }

        List<EduImportPreviewRow> rows = handler.preview(file, "{}");

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getErrors().contains("文件内账号重复")));
    }

    private byte[] workbook(String[] values) throws Exception {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            write(workbook.getSheet("账号数据").createRow(1), values);
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
    }

    private DeptDO dept(String code) {
        DeptDO dept = new DeptDO();
        dept.setId(1L);
        dept.setDeptCode(code);
        return dept;
    }

    private void write(Row row, String... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index, CellType.STRING).setCellValue(values[index]);
        }
    }

}
