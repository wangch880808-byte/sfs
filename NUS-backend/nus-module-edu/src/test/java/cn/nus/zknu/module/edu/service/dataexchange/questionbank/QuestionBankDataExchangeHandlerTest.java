package cn.nus.zknu.module.edu.service.dataexchange.questionbank;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class QuestionBankDataExchangeHandlerTest extends BaseMockitoUnitTest {

    @Mock
    private CapacityIndicatorApi indicatorMapper;
    @Mock
    private EduQuestionBankMapper questionBankMapper;

    private QuestionBankDataExchangeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new QuestionBankDataExchangeHandler();
        ReflectionTestUtils.setField(handler, "capacityIndicatorApi", indicatorMapper);
        ReflectionTestUtils.setField(handler, "questionBankMapper", questionBankMapper);
        lenient().when(questionBankMapper.selectImportByTeacherId(any())).thenReturn(List.of());
    }

    @Test
    void preview_validQuestionResolvesIndicatorHierarchy() throws Exception {
        CapacityIndicatorRespDTO first = indicator(1L, "专业核心能力", 1, null);
        CapacityIndicatorRespDTO second = indicator(2L, "Java应用开发", 2, 1L);
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(first, second));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            List<EduImportPreviewRow> rows = handler.preview(workbook(
                    "专业核心能力", "Java应用开发", "中级", "学生管理", "完成增删改查"), "{}");

            assertEquals(1, rows.size());
            assertTrue(rows.get(0).getErrors().isEmpty());
            assertEquals("INSERT", rows.get(0).getAction().name());
            verify(questionBankMapper).selectImportByTeacherId(7L);
            verify(questionBankMapper, never()).selectList();
        }
    }

    @Test
    void preview_existingTeacherQuestionKeepsDuplicateKeyAndFirstSnapshot() throws Exception {
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(
                indicator(1L, "专业核心能力", 1, null), indicator(2L, "Java应用开发", 2, 1L)));
        var first = new cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO();
        first.setId(10L); first.setTeacherId(7L); first.setIndicatorFirstId(1L); first.setIndicatorSecondId(2L);
        first.setHomeworkLevel(2); first.setTitle("学生管理"); first.setRequirement("完成增删改查");
        var second = new cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO();
        org.springframework.beans.BeanUtils.copyProperties(first, second);
        second.setId(11L); second.setTitle(" 学生管理 ");
        when(questionBankMapper.selectImportByTeacherId(7L)).thenReturn(List.of(first, second));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var rows = handler.preview(workbook("专业核心能力", "Java应用开发", "中级", "学生管理", "完成增删改查"), "{}");
            assertEquals("SKIP", rows.get(0).getAction().name());
            assertEquals("学生管理", JSONUtil.parseObj(rows.get(0).getBeforeData()).getStr("title"));
            verify(questionBankMapper).selectImportByTeacherId(7L);
            verify(questionBankMapper, never()).selectList();
        }
    }

    @Test
    void preview_wrongIndicatorRelationIsRejected() throws Exception {
        CapacityIndicatorRespDTO first = indicator(1L, "专业核心能力", 1, null);
        CapacityIndicatorRespDTO second = indicator(2L, "Java应用开发", 2, 9L);
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(first, second));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            List<EduImportPreviewRow> rows = handler.preview(workbook(
                    "专业核心能力", "Java应用开发", "中级", "学生管理", "完成增删改查"), "{}");

            assertTrue(rows.get(0).getErrors().stream().anyMatch(value -> value.contains("不属于当前一级指标")));
        }
    }

    @Test
    void generate_addsIndicatorCascadeAndLevelDropdowns() throws Exception {
        CapacityIndicatorRespDTO first = indicator(1L, "专业核心能力", 1, null);
        CapacityIndicatorRespDTO second = indicator(2L, "Java应用开发", 2, 1L);
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(first, second));

        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            var sheet = workbook.getSheet("题库数据");
            assertEquals(List.of("一级能力指标*", "二级能力指标*", "任务等级*", "任务标题*", "任务要求*"),
                    java.util.stream.IntStream.range(0, 5)
                            .mapToObj(column -> sheet.getRow(0).getCell(column).getStringCellValue()).toList());
            assertEquals("V2", handler.getTemplateVersion());
            assertEquals(3, sheet.getDataValidations().size());
            assertTrue(sheet.getDataValidations().stream()
                    .anyMatch(validation -> validation.getValidationConstraint().getFormula1().contains("VLOOKUP($A2")));
            assertNotNull(workbook.getName("NUS_DEP_0_0"));
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("级联选项")));
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("下拉选项")));
        }
    }

    @Test
    void preview_indicatorLabelsWithIdsResolveDuplicateNamesExactly() throws Exception {
        CapacityIndicatorRespDTO first = indicator(1L, "专业核心能力", 1, null);
        CapacityIndicatorRespDTO secondOne = indicator(2L, "Java应用开发", 2, 1L);
        CapacityIndicatorRespDTO secondTwo = indicator(3L, "Java应用开发", 2, 1L);
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(first, secondOne, secondTwo));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            List<EduImportPreviewRow> rows = handler.preview(workbook(
                    "专业核心能力 [ID:1]", "Java应用开发 [ID:3]", "中级", "学生管理", "完成增删改查"), "{}");

            assertTrue(rows.get(0).getErrors().isEmpty());
            QuestionBankImportRow row = JSONUtil.toBean(rows.get(0).getNormalizedData(), QuestionBankImportRow.class);
            assertEquals(3L, row.getIndicatorSecondId());
        }
    }

    @Test
    void preview_legacyPlainNameRejectsAmbiguousIndicator() throws Exception {
        CapacityIndicatorRespDTO first = indicator(1L, "专业核心能力", 1, null);
        CapacityIndicatorRespDTO secondOne = indicator(2L, "Java应用开发", 2, 1L);
        CapacityIndicatorRespDTO secondTwo = indicator(3L, "Java应用开发", 2, 1L);
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(first, secondOne, secondTwo));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            List<EduImportPreviewRow> rows = handler.preview(workbook(
                    "专业核心能力", "Java应用开发", "中级", "学生管理", "完成增删改查"), "{}");

            assertTrue(rows.get(0).getErrors().stream().anyMatch(value -> value.contains("名称重复")));
        }
    }

    @Test
    void preview_acceptsTemplateDownloadedBeforeHeaderRename() throws Exception {
        CapacityIndicatorRespDTO first = indicator(1L, "专业核心能力", 1, null);
        CapacityIndicatorRespDTO second = indicator(2L, "Java应用开发", 2, 1L);
        when(indicatorMapper.getIndicatorTreeList()).thenReturn(List.of(first, second));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            byte[] legacyTemplate;
            try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
                var sheet = workbook.getSheet("题库数据");
                write(sheet.getRow(0), "一级技能指标*", "二级技能指标*", "技能等级*");
                write(sheet.createRow(1), "专业核心能力", "Java应用开发", "中级", "学生管理", "完成增删改查");
                legacyTemplate = EduXlsxWorkbookUtils.writeWorkbook(workbook);
            }

            List<EduImportPreviewRow> rows = handler.preview(legacyTemplate, "{}");

            assertEquals(1, rows.size());
            assertTrue(rows.get(0).getErrors().isEmpty());
            assertEquals("INSERT", rows.get(0).getAction().name());
        }
    }

    private byte[] workbook(String... values) throws Exception {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(handler.generate())) {
            write(workbook.getSheet("题库数据").createRow(1), values);
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
    }

    private CapacityIndicatorRespDTO indicator(Long id, String name, Integer level, Long parentId) {
        CapacityIndicatorRespDTO indicator = new CapacityIndicatorRespDTO();
        indicator.setId(id);
        indicator.setIndicatorName(name);
        indicator.setIndicatorLevel(level);
        indicator.setParentIndicatorId(parentId);
        indicator.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return indicator;
    }

    private void write(Row row, String... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index, CellType.STRING).setCellValue(values[index]);
        }
    }

}
