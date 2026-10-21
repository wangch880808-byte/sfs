package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.common.exception.ServiceException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EduXlsxWorkbookUtilsTest {

    @Test
    void createTemplate_addsReadableInputDesignWithoutCreatingImportRows() {
        List<String> headers = List.of("学号*", "姓名*", "性别", "邮箱", "状态", "备注");
        byte[] content = EduXlsxWorkbookUtils.createTemplate("STUDENT", "V1", List.of("规则"),
                List.of(new EduXlsxWorkbookUtils.SheetSpec("学生档案", headers, Set.of(0),
                        List.of("20260001", "张三", "男", "student@example.edu", "正常", "示例"))),
                List.of());

        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(content)) {
            var sheet = workbook.getSheet("学生档案");
            assertNotNull(workbook.getSheet("填写示例"));
            assertEquals(26 * 256, sheet.getColumnWidth(0));
            assertEquals(32 * 256, sheet.getColumnWidth(3));
            assertEquals(IndexedColors.LIGHT_YELLOW.getIndex(),
                    sheet.getRow(1).getCell(0).getCellStyle().getFillForegroundColor());
            assertEquals(2, sheet.getDataValidations().size());
            assertTrue(sheet.isSelected());
            assertFalse(workbook.getSheet("模板信息").isSelected());
            assertEquals(1, java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                    .filter(index -> workbook.getSheetAt(index).isSelected())
                    .count());
            assertTrue(EduXlsxWorkbookUtils.readSheet(workbook, "学生档案", headers).isEmpty());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void createTemplate_usesHiddenNamedRangeForDynamicDropdown() {
        List<String> options = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> "计算机学院 / 软件工程 [MAJOR-" + index + "]")
                .toList();
        byte[] content = EduXlsxWorkbookUtils.createTemplate("STUDENT", "V1", List.of("规则"),
                List.of(new EduXlsxWorkbookUtils.SheetSpec(
                        "学生档案", List.of("学号*", "主专业编码*"), Set.of(0, 1))),
                List.of(), java.util.Map.of("主专业编码", options));

        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(content)) {
            var sheet = workbook.getSheet("学生档案");
            assertEquals(1, sheet.getDataValidations().size());
            assertEquals("NUS_DROPDOWN_0",
                    sheet.getDataValidations().get(0).getValidationConstraint().getFormula1());
            assertNotNull(workbook.getName("NUS_DROPDOWN_0"));
            assertEquals("'下拉选项'!$A$2:$A$101",
                    workbook.getName("NUS_DROPDOWN_0").getRefersToFormula());
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("下拉选项")));
            assertFalse(workbook.getSheet("下拉选项").getRow(100).getCell(0).getStringCellValue().isBlank());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void createTemplate_usesParentSelectionForDependentDropdown() {
        Map<String, List<String>> childOptions = new LinkedHashMap<>();
        childOptions.put("书写技能 [ID:1]", List.of("粉笔字 [ID:2]", "钢笔字 [ID:3]"));
        childOptions.put("表达技能 [ID:6]", List.of("普通话 [ID:7]"));
        byte[] content = EduXlsxWorkbookUtils.createTemplate("QUESTION_BANK", "V2", List.of("规则"),
                List.of(new EduXlsxWorkbookUtils.SheetSpec("题库数据",
                        List.of("一级能力指标*", "二级能力指标*", "任务等级*"), Set.of())),
                List.of(),
                Map.of("一级能力指标", List.copyOf(childOptions.keySet()),
                        "任务等级", List.of("专业", "初级", "中级", "高级")),
                List.of(new EduXlsxWorkbookUtils.DependentDropdownSpec(
                        "一级能力指标", "二级能力指标", childOptions)));

        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(content)) {
            var sheet = workbook.getSheet("题库数据");
            assertEquals(3, sheet.getDataValidations().size());
            assertTrue(sheet.getDataValidations().stream()
                    .anyMatch(validation -> "INDIRECT(IFERROR(VLOOKUP($A2,NUS_DEP_MAP_0,2,FALSE),\"NUS_DEP_EMPTY_0\"))"
                            .equals(validation.getValidationConstraint().getFormula1())));
            assertNotNull(workbook.getName("NUS_DEP_MAP_0"));
            assertNotNull(workbook.getName("NUS_DEP_0_0"));
            assertNotNull(workbook.getName("NUS_DEP_0_1"));
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("级联选项")));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void readSheet_acceptsHeadersRenamedSinceEarlierTemplates() throws Exception {
        List<String> headers = List.of("一级能力指标*", "二级能力指标*", "任务等级*", "任务标题*", "任务要求*");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("题库数据");
            writeRow(sheet.createRow(0), "一级技能指标*", "二级技能指标*", "技能等级*", "任务标题*", "任务要求*");
            writeRow(sheet.createRow(1), "专业核心能力", "Java 应用开发", "中级", "学生管理", "完成增删改查");

            List<EduXlsxWorkbookUtils.RowData> rows = EduXlsxWorkbookUtils.readSheet(workbook, "题库数据", headers);

            assertEquals(1, rows.size());
            assertEquals("专业核心能力", rows.get(0).get("一级能力指标*"));
            assertEquals("Java 应用开发", rows.get(0).get("二级能力指标*"));
            assertEquals("中级", rows.get(0).get("任务等级*"));
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("班级关系");
            writeRow(sheet.createRow(0), "学号*", "班级ID*", "备注");
            writeRow(sheet.createRow(1), "20260001", "12", "");

            List<EduXlsxWorkbookUtils.RowData> rows = EduXlsxWorkbookUtils.readSheet(
                    workbook, "班级关系", List.of("学号*", "班级编号*", "备注"));

            assertEquals("12", rows.get(0).get("班级编号*"));
        }
    }

    @Test
    void readSheet_rejectsHeadersThatAreNeitherCurrentNorRenamed() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeRow(workbook.createSheet("班级关系").createRow(0), "学号*", "班级*", "备注");

            assertThrows(ServiceException.class, () -> EduXlsxWorkbookUtils.readSheet(
                    workbook, "班级关系", List.of("学号*", "班级编号*", "备注")));
        }
    }

    private static void writeRow(Row row, String... values) {
        for (int column = 0; column < values.length; column++) {
            row.createCell(column, CellType.STRING).setCellValue(values[column]);
        }
    }

}
