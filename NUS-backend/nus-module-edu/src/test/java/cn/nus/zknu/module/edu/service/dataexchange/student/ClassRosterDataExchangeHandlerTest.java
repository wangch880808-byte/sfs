package cn.nus.zknu.module.edu.service.dataexchange.student;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassRosterDataExchangeHandlerTest extends BaseMockitoUnitTest {

    @Mock
    private EduDeptScopeService deptScopeService;

    @Mock
    private StudentDataExchangeSupport support;

    private ClassRosterDataExchangeHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(deptScopeService.isDeptCodeInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isUsernameInScope(any())).thenReturn(true);
        lenient().when(deptScopeService.isClassInScope(any())).thenReturn(true);
        lenient().doCallRealMethod().when(deptScopeService).markOutOfScope(any());
        lenient().doCallRealMethod().when(deptScopeService).markSkipped(any(), any());
        handler = new ClassRosterDataExchangeHandler();
        ReflectionTestUtils.setField(handler, "support", support);
        ReflectionTestUtils.setField(handler, "deptScopeService", deptScopeService);
    }

    @Test
    void preview_newStudentPreservesLeadingZeroAndInfersClassMajor() throws Exception {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(3L);
        clazz.setDeptId(9L);
        clazz.setGradeYear(2026);
        DeptDO major = new DeptDO();
        major.setId(9L);
        major.setDeptCode("M001");
        when(support.getClass(3L)).thenReturn(clazz);
        when(support.getDept(9L)).thenReturn(major);

        byte[] file;
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            Row row = workbook.getSheet("班级花名册").createRow(1);
            String[] values = {"00001234", "张三", "男", "", "", ""};
            for (int index = 0; index < values.length; index++) {
                row.createCell(index, CellType.STRING).setCellValue(values[index]);
            }
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }

        List<EduImportPreviewRow> previews = handler.preview(file, "{\"classId\":3}");

        assertEquals(1, previews.size());
        assertTrue(previews.get(0).getErrors().isEmpty());
        ClassRosterImportRow normalized = JSONUtil.toBean(previews.get(0).getNormalizedData(), ClassRosterImportRow.class);
        assertEquals("00001234", normalized.getUsername());
        assertEquals("M001", normalized.getMajorCode());
        assertEquals("INSERT", normalized.getAccountAction());
        assertEquals("INSERT", normalized.getRelationAction());
    }

    @Test
    void preview_marksEveryRowInDuplicateGroup() throws Exception {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(3L);
        clazz.setDeptId(9L);
        clazz.setGradeYear(2026);
        DeptDO major = new DeptDO();
        major.setId(9L);
        major.setDeptCode("M001");
        when(support.getClass(3L)).thenReturn(clazz);
        when(support.getDept(9L)).thenReturn(major);

        byte[] file;
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            for (int rowIndex = 1; rowIndex <= 2; rowIndex++) {
                Row row = workbook.getSheet("班级花名册").createRow(rowIndex);
                String[] values = {"00001234", "张三", "男", "13800138000", "same@example.edu", ""};
                for (int column = 0; column < values.length; column++) {
                    row.createCell(column, CellType.STRING).setCellValue(values[column]);
                }
            }
            file = EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }

        List<EduImportPreviewRow> previews = handler.preview(file, "{\"classId\":3}");

        assertEquals(2, previews.size());
        for (EduImportPreviewRow preview : previews) {
            assertTrue(preview.getErrors().contains("文件内学号重复"));
            assertTrue(preview.getErrors().contains("文件内手机号重复"));
            assertTrue(preview.getErrors().contains("文件内邮箱重复"));
        }
    }

}
