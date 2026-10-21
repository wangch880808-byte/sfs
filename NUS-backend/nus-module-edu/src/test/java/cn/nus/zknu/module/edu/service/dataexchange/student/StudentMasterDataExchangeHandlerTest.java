package cn.nus.zknu.module.edu.service.dataexchange.student;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentMasterDataExchangeHandlerTest extends BaseMockitoUnitTest {

    @Mock
    private EduDeptScopeService deptScopeService;
    @Mock
    private StudentDataExchangeSupport support;
    private StudentMasterDataExchangeHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(deptScopeService.isUsernameInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isClassInScope(any())).thenReturn(true);
        lenient().doCallRealMethod().when(deptScopeService).markOutOfScope(any());
        lenient().doCallRealMethod().when(deptScopeService).markSkipped(any(), any());
        lenient().doCallRealMethod().when(support).studentSnapshot(any(StudentAccountImportRow.class));
        lenient().doCallRealMethod().when(support).studentSnapshot(any(AdminUserDO.class));
        lenient().doCallRealMethod().when(support).relationSnapshot(any());
        lenient().doCallRealMethod().when(support).isRemarkRequired(any(), any(), any());
        lenient().doCallRealMethod().when(support).validateNoIdentityConflict(any(), anyList());
        lenient().doCallRealMethod().when(support).validateContactUniqueness(any(), any(), any(), anyList());
        // 旧测试的数据夹具同时提供给新增的批量接口，原校验断言保持不变。
        lenient().when(support.getUsersByUsernames(any())).thenAnswer(inv -> {
            Map<String, AdminUserDO> users = new java.util.HashMap<>();
            for (String name : inv.<java.util.Collection<String>>getArgument(0)) {
                AdminUserDO user = support.getUser(name);
                if (user != null) users.put(name, user);
            }
            return users;
        });
        lenient().when(support.getScopeUsersByUsernames(any())).thenAnswer(inv -> {
            Map<String, AdminUserDO> users = new java.util.HashMap<>();
            for (String name : inv.<java.util.Collection<String>>getArgument(0))
                users.put(name, new AdminUserDO().setUsername(name));
            return users;
        });
        lenient().when(support.getClasses(any())).thenAnswer(inv -> inv.<java.util.Collection<Long>>getArgument(0)
                .stream().map(support::getClass).filter(java.util.Objects::nonNull).toList());
        lenient().when(support.getScopeClasses(any())).thenAnswer(inv -> inv.<java.util.Collection<Long>>getArgument(0)
                .stream().map(support::getClass).filter(java.util.Objects::nonNull).toList());
        lenient().when(support.getDepts(any())).thenAnswer(inv -> inv.<java.util.Collection<Long>>getArgument(0)
                .stream().map(support::getDept).filter(java.util.Objects::nonNull).toList());
        lenient().when(support.getDeptsByCodes(any())).thenAnswer(inv -> {
            Map<String, DeptDO> depts = new java.util.HashMap<>();
            for (String code : inv.<java.util.Collection<String>>getArgument(0)) {
                DeptDO dept = support.getDept(code);
                if (dept != null) depts.put(code, dept);
            }
            return depts;
        });
        lenient().when(support.getProfileMap(any())).thenAnswer(inv -> {
            Map<Long, UserProfileDO> profiles = new java.util.HashMap<>();
            for (Long id : inv.<java.util.Collection<Long>>getArgument(0)) {
                UserProfileDO profile = support.getProfile(id);
                if (profile != null) profiles.put(id, profile);
            }
            return profiles;
        });
        lenient().when(support.getImportRelations(any(), any())).thenAnswer(inv -> {
            List<EduClassStudentDO> relations = new java.util.ArrayList<>();
            for (Long classId : inv.<java.util.Collection<Long>>getArgument(0))
                for (Long userId : inv.<java.util.Collection<Long>>getArgument(1)) {
                    EduClassStudentDO relation = support.getRelation(classId, userId);
                    if (relation != null) relations.add(relation);
                }
            return relations;
        });
        lenient().doCallRealMethod().when(support).studentSnapshot(any(AdminUserDO.class), any(), any());
        lenient().when(deptScopeService.isLoadedClassInScope(any())).thenAnswer(inv -> {
            EduClassDO clazz = inv.getArgument(0);
            return deptScopeService.isClassInScope(clazz == null ? null : clazz.getId());
        });
        lenient().when(deptScopeService.isLoadedUsernameInScope(any())).thenAnswer(inv -> {
            AdminUserDO user = inv.getArgument(0);
            return deptScopeService.isUsernameInScope(user == null ? null : user.getUsername());
        });
        handler = new StudentMasterDataExchangeHandler();
        ReflectionTestUtils.setField(handler, "support", support);
        ReflectionTestUtils.setField(handler, "deptScopeService", deptScopeService);
    }

    @Test
    void preview_fiftyStudentsUseConstantBatchQueries() throws Exception {
        reset(support, deptScopeService); // 独立批量夹具：任何单条查询都会令 verify 失败。
        EduClassDO clazz = clazz(3L, 9L, 2026);
        DeptDO major = major(9L, "M001");
        Map<String, AdminUserDO> users = new java.util.HashMap<>();
        List<List<String>> input = new java.util.ArrayList<>();
        for (int index = 0; index < 50; index++) {
            String username = "2026" + String.format("%04d", index);
            users.put(username, student(100L + index, username, "学生" + index));
            input.add(List.of(username, "学生" + index, "", "3", "", "", ""));
        }
        when(support.getUsersByUsernames(any())).thenReturn(users);
        when(support.getScopeUsersByUsernames(any())).thenReturn(users);
        when(support.getClasses(any())).thenReturn(List.of(clazz));
        when(support.getScopeClasses(any())).thenReturn(List.of(clazz));
        when(support.getDepts(any())).thenReturn(List.of(major));
        when(support.getDeptsByCodes(any())).thenReturn(Map.of("M001", major));
        doCallRealMethod().when(support).studentSnapshot(any(StudentAccountImportRow.class));
        doCallRealMethod().when(support).studentSnapshot(any(AdminUserDO.class), any(), any());
        doCallRealMethod().when(support).relationSnapshot(any());
        when(deptScopeService.isLoadedClassInScope(any())).thenReturn(true);
        when(deptScopeService.isLoadedUsernameInScope(any())).thenReturn(true);
        var result = handler.preview(workbook(input, List.of()), "{}");
        assertEquals(100, result.size());
        assertTrue(result.stream().allMatch(row -> row.getErrors().isEmpty()));
        verify(support).getUsersByUsernames(argThat(names -> names.size() == 50));
        verify(support).getScopeUsersByUsernames(any());
        verify(support).getClasses(any());
        verify(support).getScopeClasses(any());
        verify(support).getImportRelations(any(), argThat(ids -> ids.size() == 50));
        verify(support, never()).getUser(any());
        verify(support, never()).getClass(any());
        verify(support, never()).getRelation(any(), any());
        verify(support, never()).getDept(any(String.class));
        verify(support, never()).getDept(any(Long.class));
        verify(support, never()).getProfile(any());
        verify(deptScopeService, never()).isClassInScope(any());
        verify(deptScopeService, never()).isUsernameInScope(any());
    }

    @Test
    void generate_headersClassDropdownTextColumnsAndExample() throws Exception {
        EduClassDO clazz = clazz(3L, 9L, 2026);
        when(support.getActiveClasses()).thenReturn(List.of(clazz));
        try (XSSFWorkbook template = EduXlsxWorkbookUtils.open(handler.generate())) {
            Sheet sheet = template.getSheet("学生档案");
            assertEquals(List.of("学号*", "姓名*", "性别", "班级*", "手机号", "邮箱", "状态"),
                    headers(sheet));
            assertEquals(List.of("学号*", "班级编号*", "备注"), headers(template.getSheet("班级关系")));
            assertEquals("班级3 [3]", template.getSheet("下拉选项").getRow(1).getCell(0).getStringCellValue());
            assertEquals("班级3 [3]", template.getSheet("下拉选项").getRow(1).getCell(1).getStringCellValue());
            assertEquals("NUS_DROPDOWN_0", sheet.getDataValidations().stream()
                    .filter(validation -> validation.getRegions().getCellRangeAddresses()[0].getFirstColumn() == 3)
                    .findFirst().orElseThrow().getValidationConstraint().getFormula1());
            assertEquals("NUS_DROPDOWN_1", template.getSheet("班级关系").getDataValidations().stream()
                    .filter(validation -> validation.getRegions().getCellRangeAddresses()[0].getFirstColumn() == 1)
                    .findFirst().orElseThrow().getValidationConstraint().getFormula1());
            assertEquals("@", sheet.getColumnStyle(0).getDataFormatString());
            assertEquals("@", sheet.getColumnStyle(4).getDataFormatString());
            assertNotEquals("@", sheet.getColumnStyle(3).getDataFormatString());
            assertTrue(template.getSheet("模板信息").getRow(4).getCell(1).getStringCellValue()
                    .contains("专业和年级按班级自动补全"));
            assertTrue(template.getSheet("填写示例").getRow(2).getCell(3).getStringCellValue().contains("[1]"));
        }
        assertEquals("V1", handler.getTemplateVersion());
        assertEquals("学生主数据导入模板_V1.xlsx", handler.getFileName());
        verify(support, never()).getActiveDepartments();
    }

    @Test
    void preview_classOnlyInfersMajorAndYearAndCommitCreatesStudentBeforeRelation() throws Exception {
        activeClass(3L, 9L, "M001", 2010);
        List<EduImportPreviewRow> previews = handler.preview(studentWorkbook("00001234", "张三", "班级3 [3]"), "{}");
        assertEquals(2, previews.size());
        assertTrue(previews.stream().allMatch(item -> item.getErrors().isEmpty()));
        StudentAccountImportRow student = account(previews.get(0));
        assertEquals("00001234", student.getUsername());
        assertEquals("M001", student.getMajorCode());
        assertEquals(2010, student.getGradeYear());
        assertEquals(3L, student.getClassId());
        assertEquals("INSERT", previews.get(0).getAction().name());
        assertEquals("REL:00001234:3", previews.get(1).getBusinessKey());
        assertEquals(StudentMasterDataExchangeHandler.AUTO_RELATION_SHEET, previews.get(1).getSheetName());
        assertEquals(2, previews.get(1).getRowNumber());
        // 导入批次要求「工作表 + 行号」唯一，自动入班行不能和学生行重复
        assertEquals(previews.size(), previews.stream()
                .map(row -> row.getSheetName() + ":" + row.getRowNumber()).distinct().count());
        assertEquals("INSERT", previews.get(1).getAction().name());
        AdminUserDO created = student(5L, "00001234", "张三");
        doAnswer(invocation -> {
            lenient().when(support.getUser("00001234")).thenReturn(created);
            return 5L;
        }).when(support).saveStudent(any(), eq("initial-password"));
        // 反序输入也必须先保存学生，再提交同工作表中的关系行。
        var result = handler.commit(null, List.of(item(previews.get(1)), item(previews.get(0))), "initial-password");
        assertEquals(2, result.insertedCount());
        var order = inOrder(support);
        order.verify(support).saveStudent(any(), eq("initial-password"));
        order.verify(support).addOrRestoreRelation(3L, 5L, null);
    }

    @Test
    void preview_requiresClassEvenForExistingStudent() throws Exception {
        AdminUserDO existing = student(5L, "00001234", "原姓名");
        lenient().when(support.getUser("00001234")).thenReturn(existing);
        List<EduImportPreviewRow> rows = handler.preview(studentWorkbook("00001234", "张三", ""), "{}");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().contains("班级不能为空"));
    }

    @Test
    void preview_nonIntegerClassIsRejected() throws Exception {
        List<EduImportPreviewRow> rows = handler.preview(studentWorkbook("00001234", "张三", "班级 [abc]"), "{}");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().contains("班级编号必须是整数"));
    }

    @Test
    void preview_missingOrArchivedClassIsRejected() throws Exception {
        List<EduImportPreviewRow> missing = handler.preview(studentWorkbook("00001234", "张三", "99"), "{}");
        assertTrue(missing.get(0).getErrors().contains("班级不存在或已封存"));
        EduClassDO archived = clazz(3L, 9L, 2026);
        archived.setStatus(1);
        when(support.getClass(3L)).thenReturn(archived);
        List<EduImportPreviewRow> rows = handler.preview(studentWorkbook("00001234", "张三", "3"), "{}");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().contains("班级不存在或已封存"));
    }

    @Test
    void preview_classOrganizationMustBeMajor() throws Exception {
        EduClassDO clazz = clazz(3L, 9L, 2026);
        DeptDO dept = major(9L, "CS");
        dept.setDeptType("COLLEGE");
        when(support.getClass(3L)).thenReturn(clazz);
        lenient().when(support.getDept(9L)).thenReturn(dept);
        List<EduImportPreviewRow> rows = handler.preview(studentWorkbook("00001234", "张三", "3"), "{}");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().contains("班级所属组织不是专业，请先在组织架构中调整"));
    }

    @Test
    void preview_missingClassOrganizationIsRejected() throws Exception {
        when(support.getClass(3L)).thenReturn(clazz(3L, 9L, 2026));
        var rows = handler.preview(studentWorkbook("00001234", "张三", "3"), "{}");
        assertTrue(rows.get(0).getErrors().contains("班级所属组织不是专业，请先在组织架构中调整"));
    }

    @Test
    void preview_rejectsLegacyHeaders() throws Exception {
        byte[] file = EduXlsxWorkbookUtils.createTemplate("STUDENT_MASTER", "V1", List.of(), List.of(
                new EduXlsxWorkbookUtils.SheetSpec("学生档案", List.of("学号*", "姓名*", "性别", "主专业编码*",
                        "入学年份*", "手机号", "邮箱", "状态"), java.util.Set.of(0, 3, 5)),
                new EduXlsxWorkbookUtils.SheetSpec("班级关系", StudentMasterDataExchangeHandler.RELATION_HEADERS,
                        java.util.Set.of(0, 1))), List.of());
        ServiceException ex = assertThrows(ServiceException.class, () -> handler.preview(file, "{}"));
        assertEquals(IMPORT_PREVIEW_INVALID.getCode(), ex.getCode());
    }

    @Test
    void preview_automaticAndExplicitRelationAreDeduplicated() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        var rows = handler.preview(workbook(List.of(List.of("00001234", "张三", "", "3", "", "", "")),
                List.of(List.of("00001234", "班级3 [3]", ""))), "{}");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getErrors().isEmpty()));
        assertEquals(StudentMasterDataExchangeHandler.AUTO_RELATION_SHEET, rows.get(1).getSheetName());
    }

    @Test
    void preview_extraClassesRequireRemarkAcrossMajorOrYearAndKeepExplicitDuplicates() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        activeClass(4L, 10L, "M002", 2026);
        activeClass(5L, 9L, "M001", 2025);
        var rows = handler.preview(workbook(List.of(List.of("00001234", "张三", "", "3", "", "", "")),
                List.of(List.of("00001234", "4", ""), List.of("00001234", "5", "跨年级选修"),
                        List.of("00001234", "5", "跨年级选修"))), "{}");
        assertEquals(5, rows.size());
        assertTrue(rows.get(2).getErrors().contains("跨专业或跨年级加入班级必须填写备注"));
        assertFalse(rows.get(3).getErrors().contains("跨专业或跨年级加入班级必须填写备注"));
        assertTrue(rows.get(3).getErrors().contains("文件内班级关系重复"));
        assertTrue(rows.get(4).getErrors().contains("文件内班级关系重复"));
    }

    @Test
    void preview_disabledStudentCannotAutomaticallyJoinClass() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        var rows = handler.preview(workbook(List.of(List.of("00001234", "张三", "", "3", "", "", "停用")),
                List.of()), "{}");
        assertTrue(rows.get(0).getErrors().isEmpty());
        assertTrue(rows.get(1).getErrors().contains("停用学生不能加入班级"));
    }

    @Test
    void preview_restoresDeletedRelationWithSameSnapshotRules() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        AdminUserDO existing = student(5L, "00001234", "张三");
        lenient().when(support.getUser("00001234")).thenReturn(existing);
        EduClassStudentDO relation = relation(3L, 5L, "原备注");
        relation.setDeleted(true);
        lenient().when(support.getRelation(3L, 5L)).thenReturn(relation);
        var rows = handler.preview(studentWorkbook("00001234", "张三", "3"), "{}");
        assertEquals("UPDATE", rows.get(1).getAction().name());
        assertEquals("DELETED", JSONUtil.parseObj(rows.get(1).getBeforeData()).getStr("state"));
        assertEquals("ACTIVE", JSONUtil.parseObj(rows.get(1).getAfterData()).getStr("state"));
        assertEquals(1, handler.commit(null, List.of(item(rows.get(1))), null).updatedCount());
        verify(support).addOrRestoreRelation(3L, 5L, null);
    }

    @Test
    void preview_existingNonStudentIsRejected() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        AdminUserDO teacher = student(5L, "T0001", "教师账号");
        teacher.setUserType(1);
        lenient().when(support.getUser("T0001")).thenReturn(teacher);
        var rows = handler.preview(studentWorkbook("T0001", "教师账号", "3"), "{}");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().contains("学号已被非学生账号占用"));
    }

    @Test
    void preview_existingStudentRetainsBlankAccountFieldsButOverwritesMajorAndYear() throws Exception {
        activeClass(3L, 10L, "M002", 2026);
        DeptDO originalMajor = major(9L, "M001");
        lenient().when(support.getDept(9L)).thenReturn(originalMajor);
        AdminUserDO existing = student(5L, "00001234", "原姓名");
        existing.setSex(2);
        existing.setMobile("13800138000");
        existing.setEmail("student@example.com");
        lenient().when(support.getUser("00001234")).thenReturn(existing);
        lenient().when(support.getProfile(5L)).thenReturn(profile(5L, 2025));
        var rows = handler.preview(studentWorkbook("00001234", "", "3"), "{}");
        assertTrue(rows.get(0).getErrors().isEmpty());
        assertEquals("UPDATE", rows.get(0).getAction().name());
        StudentAccountImportRow normalized = account(rows.get(0));
        assertEquals("原姓名", normalized.getNickname());
        assertEquals(2, normalized.getSex());
        assertEquals("M002", normalized.getMajorCode());
        assertEquals(2026, normalized.getGradeYear());
        assertEquals("13800138000", normalized.getMobile());
        assertEquals("student@example.com", normalized.getEmail());
    }

    @Test
    void preview_accountValidationAndContactUniquenessStillApply() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        AdminUserDO owner = student(7L, "OTHER", "其他用户");
        cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO mobileOwner =
                new cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO();
        mobileOwner.setId(owner.getId());
        mobileOwner.setUsername(owner.getUsername());
        mobileOwner.setLookupKey("13800138000");
        when(support.getMobileOwners(any())).thenReturn(List.of(mobileOwner));
        cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO emailOwner =
                new cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO();
        emailOwner.setId(owner.getId());
        emailOwner.setUsername(owner.getUsername());
        emailOwner.setLookupKey("student@example.com");
        when(support.getEmailOwners(any())).thenReturn(List.of(emailOwner));
        var rows = handler.preview(workbook(List.of(List.of("bad!", "", "", "3", "13800138000",
                "student@example.com", "")), List.of()), "{}");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getErrors().containsAll(List.of("学号必须为 4 至 30 位字母或数字", "姓名不能为空",
                "手机号已被账号「OTHER」使用", "邮箱已被账号「OTHER」使用")));
    }

    @Test
    void preview_invalidDuplicateStudentsDoNotGenerateRelations() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        var rows = handler.preview(workbook(List.of(List.of("00001234", "张三", "", "3", "", "", ""),
                List.of("00001234", "张三", "", "3", "", "", "")), List.of()), "{}");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getErrors().contains("文件内学号重复")));
    }

    @Test
    void preview_marksForeignClassStudentAndRelationsOutOfScope() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        activeClass(4L, 10L, "FOREIGN", 2026);
        when(deptScopeService.isClassInScope(4L)).thenReturn(false);
        var rows = handler.preview(workbook(List.of(List.of("20260001", "本院学生", "", "3", "", "", ""),
                        List.of("20260002", "外院学生", "", "4", "", "", "")),
                List.of(List.of("20260001", "4", "跨院选修"))), "{}");
        assertEquals(5, rows.size());
        assertEquals("INSERT", rows.get(0).getAction().name());
        assertEquals("INSERT", rows.get(2).getAction().name());
        for (int index : List.of(1, 3, 4)) {
            assertEquals("SKIP", rows.get(index).getAction().name());
            assertTrue(rows.get(index).getErrors().isEmpty());
            assertEquals("不在本学院", JSONUtil.parseObj(rows.get(index).getNormalizedData()).getStr("skipReason"));
            assertNull(rows.get(index).getBeforeData());
        }
        assertEquals(3, handler.commit(null, List.of(item(rows.get(1)), item(rows.get(3)), item(rows.get(4))), null)
                .skippedCount());
        verify(support, never()).saveStudent(any(), any());
        verify(support, never()).addOrRestoreRelation(any(), any(), any());
    }

    @Test
    void preview_skipsForeignExistingStudentEvenWhenTargetClassIsOwnCollege() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        when(deptScopeService.isUsernameInScope("20260002")).thenReturn(false);
        var rows = handler.preview(studentWorkbook("20260002", "外院学生", "3"), "{}");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> "SKIP".equals(row.getAction().name())
                && "不在本学院".equals(JSONUtil.parseObj(row.getNormalizedData()).getStr("skipReason"))));
    }

    @Test
    void commit_checksStudentClassScopeUsingBusinessKey() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        var rows = handler.preview(studentWorkbook("00001234", "张三", "3"), "{}");
        when(deptScopeService.isClassInScope(3L)).thenReturn(false);
        var ex = assertThrows(ServiceException.class, () -> handler.commit(null, List.of(item(rows.get(0))), null));
        assertEquals(FORBIDDEN.getCode(), ex.getCode());
        verify(support, never()).saveStudent(any(), any());
    }

    @Test
    void commit_automaticRelationStillChecksScopeAndSnapshot() throws Exception {
        activeClass(3L, 9L, "M001", 2026);
        var rows = handler.preview(studentWorkbook("00001234", "张三", "3"), "{}");
        lenient().when(support.getUser("00001234")).thenReturn(student(5L, "00001234", "张三"));
        lenient().when(support.getRelation(3L, 5L)).thenReturn(relation(3L, 5L, "并发加入"));
        var ex = assertThrows(ServiceException.class, () -> handler.commit(null, List.of(item(rows.get(1))), null));
        assertEquals(IMPORT_DATA_CHANGED.getCode(), ex.getCode());
        verify(deptScopeService).validateClassInScope(3L);
        verify(support, never()).addOrRestoreRelation(any(), any(), any());
    }

    @Test
    void commit_doesNotReadOrWriteSkippedRowsRegardlessOfSheetName() {
        EduImportBatchItemDO student = new EduImportBatchItemDO();
        student.setSheetName("学生档案");
        student.setBusinessKey("STUDENT:foreign");
        student.setAction("SKIP");
        EduImportBatchItemDO relation = new EduImportBatchItemDO();
        relation.setSheetName("学生档案");
        relation.setBusinessKey("REL:foreign:99");
        relation.setAction("SKIP");
        assertEquals(2, handler.commit(null, List.of(student, relation), null).skippedCount());
        verifyNoInteractions(support);
    }

    @Test
    void export_primaryMatchingMajorAndYearAndRoundTripAllSkip() throws Exception {
        EduClassDO primary = activeClass(3L, 9L, "M001", 2026);
        EduClassDO otherMajor = activeClass(4L, 10L, "M002", 2026);
        EduClassDO otherYear = activeClass(5L, 9L, "M001", 2025);
        AdminUserDO user = student(5L, "00001234", "张三");
        lenient().when(support.getUser(user.getUsername())).thenReturn(user);
        UserProfileDO profile = profile(5L, 2026);
        lenient().when(support.getProfile(5L)).thenReturn(profile);
        var relations = List.of(relation(4L, 5L, "跨专业选修"), relation(5L, 5L, "跨年级选修"),
                relation(3L, 5L, "主班备注"));
        exportData(List.of(user), Map.of(5L, profile), relations, List.of(otherMajor, otherYear, primary));
        for (var relation : relations) lenient().when(support.getRelation(relation.getClassId(), 5L)).thenReturn(relation);
        byte[] file = handler.export(new UserPageReqVO());
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(file)) {
            assertEquals("班级3 [3]", workbook.getSheet("学生档案").getRow(1).getCell(3).getStringCellValue());
            var relationRows = EduXlsxWorkbookUtils.readSheet(workbook, "班级关系",
                    StudentMasterDataExchangeHandler.RELATION_HEADERS);
            assertEquals(2, relationRows.size());
            assertEquals(List.of("4", "5"), relationRows.stream().map(row -> row.get("班级编号*")).toList());
            assertEquals("跨专业选修", relationRows.get(0).get("备注"));
        }
        var rows = handler.preview(file, "{}");
        assertEquals(4, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getErrors().isEmpty() && "SKIP".equals(row.getAction().name())));
        assertEquals("主班备注", JSONUtil.parseObj(rows.get(1).getAfterData()).getStr("remark"));
        assertEquals(4, handler.commit(null, rows.stream().map(this::item).toList(), null).skippedCount());
        verify(support, never()).saveStudent(any(), any());
        verify(support, never()).addOrRestoreRelation(any(), any(), any());
    }

    @Test
    void export_fallsBackToFirstActiveClassAndLeavesStudentsWithoutClassBlank() throws Exception {
        EduClassDO archived = clazz(2L, 9L, 2026);
        archived.setStatus(1);
        EduClassDO first = clazz(4L, 10L, 2025);
        EduClassDO deleted = clazz(5L, 9L, 2026);
        AdminUserDO user = student(5L, "00001234", "张三");
        AdminUserDO noClass = student(6L, "00005678", "李四");
        EduClassStudentDO deletedRelation = relation(5L, 5L, "已删除");
        deletedRelation.setDeleted(true);
        exportData(List.of(user, noClass), Map.of(5L, profile(5L, 2026)),
                List.of(relation(2L, 5L, "已归档"), relation(4L, 5L, "选修"), deletedRelation),
                List.of(archived, first, deleted));
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.export(new UserPageReqVO()))) {
            assertEquals("班级4 [4]", workbook.getSheet("学生档案").getRow(1).getCell(3).getStringCellValue());
            assertEquals("", workbook.getSheet("学生档案").getRow(2).getCell(3).getStringCellValue());
            assertTrue(EduXlsxWorkbookUtils.readSheet(workbook, "班级关系",
                    StudentMasterDataExchangeHandler.RELATION_HEADERS).isEmpty());
        }
    }

    private void exportData(List<AdminUserDO> users, Map<Long, UserProfileDO> profiles,
                            List<EduClassStudentDO> relations, List<EduClassDO> classes) {
        when(support.getStudentList(any())).thenReturn(users);
        doReturn(profiles).when(support).getProfileMap(any());
        when(support.getActiveRelationsByStudents(any())).thenReturn(relations);
        doReturn(classes).when(support).getClasses(any());
    }

    private List<String> headers(Sheet sheet) {
        return java.util.stream.IntStream.range(0, sheet.getRow(0).getLastCellNum())
                .mapToObj(index -> sheet.getRow(0).getCell(index).getStringCellValue()).toList();
    }

    private StudentAccountImportRow account(EduImportPreviewRow preview) {
        return JSONUtil.toBean(preview.getNormalizedData(), StudentAccountImportRow.class);
    }

    private EduImportBatchItemDO item(EduImportPreviewRow preview) {
        EduImportBatchItemDO item = new EduImportBatchItemDO();
        item.setBusinessKey(preview.getBusinessKey());
        item.setSheetName(preview.getSheetName());
        item.setRowNumber(preview.getRowNumber());
        item.setAction(preview.getAction().name());
        item.setNormalizedData(preview.getNormalizedData());
        item.setBeforeData(preview.getBeforeData());
        return item;
    }

    private byte[] studentWorkbook(String username, String nickname, String classId) throws Exception {
        return workbook(List.of(List.of(username, nickname, "", classId, "", "", "")), List.of());
    }

    private byte[] workbook(List<List<String>> students, List<List<String>> relations) throws Exception {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            for (int i = 0; i < students.size(); i++) write(workbook.getSheet("学生档案").createRow(i + 1), students.get(i));
            for (int i = 0; i < relations.size(); i++) write(workbook.getSheet("班级关系").createRow(i + 1), relations.get(i));
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
    }

    private AdminUserDO student(Long id, String username, String nickname) {
        AdminUserDO user = new AdminUserDO();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setUserType(2);
        user.setDeptId(9L);
        user.setSex(0);
        user.setStatus(0);
        return user;
    }

    private UserProfileDO profile(Long userId, int year) {
        UserProfileDO profile = new UserProfileDO();
        profile.setUserId(userId);
        profile.setGradeYear(year);
        return profile;
    }

    private DeptDO major(Long id, String code) {
        DeptDO dept = new DeptDO();
        dept.setId(id);
        dept.setDeptCode(code);
        dept.setDeptType("MAJOR");
        return dept;
    }

    private EduClassDO clazz(Long id, Long deptId, int year) {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(id);
        clazz.setClassName("班级" + id);
        clazz.setDeptId(deptId);
        clazz.setGradeYear(year);
        clazz.setStatus(0);
        return clazz;
    }

    private EduClassDO activeClass(Long id, Long deptId, String majorCode, int year) {
        EduClassDO clazz = clazz(id, deptId, year);
        DeptDO major = major(deptId, majorCode);
        lenient().when(support.getClass(id)).thenReturn(clazz);
        lenient().when(support.getDept(deptId)).thenReturn(major);
        lenient().when(support.getDept(majorCode)).thenReturn(major);
        return clazz;
    }

    private EduClassStudentDO relation(Long classId, Long userId, String remark) {
        EduClassStudentDO relation = new EduClassStudentDO();
        relation.setClassId(classId);
        relation.setStudentId(userId);
        relation.setRemark(remark);
        relation.setDeleted(false);
        return relation;
    }

    private void write(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index, CellType.STRING).setCellValue(values.get(index));
        }
    }
}
