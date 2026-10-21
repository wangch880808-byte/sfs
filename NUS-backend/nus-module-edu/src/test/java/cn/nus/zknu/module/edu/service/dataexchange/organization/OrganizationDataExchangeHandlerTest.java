package cn.nus.zknu.module.edu.service.dataexchange.organization;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizationDataExchangeHandlerTest extends BaseMockitoUnitTest {

    @Mock
    private EduDeptScopeService deptScopeService;

    @Mock
    private DeptService deptService;
    @Mock
    private AdminUserService adminUserService;
    @Mock
    private EduClassService classService;

    @Mock
    private cn.nus.zknu.framework.security.core.service.SecurityFrameworkService securityFrameworkService;

    private OrganizationDataExchangeHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(deptScopeService.isExistingDeptCodeInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isDeptCodeInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isUsernameInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isClassInScope(any())).thenReturn(true);
        lenient().doCallRealMethod().when(deptScopeService).markOutOfScope(any());
        lenient().doCallRealMethod().when(deptScopeService).markSkipped(any(), any());
        handler = new OrganizationDataExchangeHandler();
        ReflectionTestUtils.setField(handler, "deptScopeService", deptScopeService);
        ReflectionTestUtils.setField(handler, "securityFrameworkService", securityFrameworkService);
        lenient().when(securityFrameworkService.hasPermission(anyString())).thenReturn(true);
        ReflectionTestUtils.setField(handler, "deptService", deptService);
        ReflectionTestUtils.setField(handler, "adminUserService", adminUserService);
        ReflectionTestUtils.setField(handler, "classService", classService);
    }

    @Test
    void collegeAdminMayImportOwnClassesButCannotMaintainOrganizations() throws Exception {
        when(securityFrameworkService.hasPermission("system:dept:create")).thenReturn(false);
        when(deptScopeService.isDeptScopedAdmin()).thenReturn(true);
        DeptDO major = new DeptDO();
        major.setId(9L);
        major.setDeptCode("M001");
        major.setDeptType("MAJOR");
        when(deptService.getDeptList(any(DeptListReqVO.class))).thenReturn(List.of(major));
        handler.validatePermission();
        byte[] file;
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            addRow(workbook.getSheet("学院专业").createRow(1),
                    "M002", "新专业", "专业", "COLLEGE", "", "", "", "", "");
            addRow(workbook.getSheet("班级").createRow(1),
                    "", "本院班级", "M001", "2026", "本科", "4", "正常", "");
            addRow(workbook.getSheet("班级").createRow(2),
                    "", "外院班级", "FOREIGN", "2026", "本科", "4", "正常", "");
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
        when(deptScopeService.isDeptCodeInScope("FOREIGN")).thenReturn(false);
        List<EduImportPreviewRow> rows = handler.preview(file, "{}");
        assertEquals("SKIP", rows.get(0).getAction().name());
        assertEquals("无组织维护权限", JSONUtil.parseObj(rows.get(0).getNormalizedData()).getStr("skipReason"));
        assertEquals("INSERT", rows.get(1).getAction().name());
        assertEquals("SKIP", rows.get(2).getAction().name());
        assertEquals("不在本学院", JSONUtil.parseObj(rows.get(2).getNormalizedData()).getStr("skipReason"));
        assertTrue(rows.stream().allMatch(row -> row.getErrors().isEmpty()));
    }

    @Test
    void generate_containsVersionedSheetsAndHiddenDictionary() throws Exception {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            assertEquals("模板信息", workbook.getSheetName(0));
            assertNotNull(workbook.getSheet("学院专业"));
            assertNotNull(workbook.getSheet("班级"));
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("数据字典")));
            assertEquals("V1", workbook.getSheet("模板信息").getRow(2).getCell(1).getStringCellValue());
        }
    }

    @Test
    void preview_preservesLeadingZeroAndResolvesOrganizationsFromSameWorkbook() throws Exception {
        when(deptService.getDeptList(any(DeptListReqVO.class))).thenReturn(Collections.emptyList());
        byte[] file;
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            addRow(workbook.getSheet("学院专业").createRow(1),
                    "S001", "示例学校", "SCHOOL", "ROOT", "1", "0", "", "", "");
            addRow(workbook.getSheet("学院专业").createRow(2),
                    "C001", "计算机学院", "COLLEGE", "S001", "2", "0", "", "", "");
            addRow(workbook.getSheet("学院专业").createRow(3),
                    "0001", "软件工程", "MAJOR", "C001", "3", "0", "", "", "");
            addRow(workbook.getSheet("班级").createRow(1),
                    "", "2026级软件工程1班", "0001", "2026", "本科", "4", "0", "");
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }

        List<EduImportPreviewRow> rows = handler.preview(file, null);

        assertEquals(4, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getErrors().isEmpty()));
        ClassImportRow classRow = JSONUtil.toBean(rows.get(3).getNormalizedData(), ClassImportRow.class);
        assertNull(classRow.getClassId());
        assertEquals("0001", classRow.getMajorCode());
    }

    @Test
    void preview_blankOptionalCellsDoNotOverwriteExistingClass() throws Exception {
        DeptDO major = new DeptDO();
        major.setId(9L);
        major.setDeptCode("M001");
        major.setDeptType("MAJOR");
        when(deptService.getDeptList(any(DeptListReqVO.class))).thenReturn(List.of(major));
        EduClassDO existing = new EduClassDO();
        existing.setId(10L);
        existing.setClassName("原班级");
        existing.setDeptId(9L);
        existing.setGradeYear(2026);
        existing.setEducationLevel(2L);
        existing.setSchoolingYear(4L);
        existing.setStatus(1);
        existing.setRemark("保留备注");
        when(classService.getClass(10L)).thenReturn(existing);

        byte[] file;
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            addRow(workbook.getSheet("班级").createRow(1),
                    "10", "新班级", "M001", "2026", "2", "4", "", "");
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }

        EduImportPreviewRow preview = handler.preview(file, null).get(0);
        ClassImportRow normalized = JSONUtil.toBean(preview.getNormalizedData(), ClassImportRow.class);
        assertEquals(1, normalized.getStatus());
        assertEquals("保留备注", normalized.getRemark());
    }

    private void addRow(Row row, String... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index, CellType.STRING).setCellValue(values[index]);
        }
    }

}
