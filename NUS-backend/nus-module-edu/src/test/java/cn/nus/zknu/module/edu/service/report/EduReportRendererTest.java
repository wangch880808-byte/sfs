package cn.nus.zknu.module.edu.service.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EduReportRendererTest {

    private final TeachingReportData data = sampleData();

    @Test
    void excelRendererCreatesReadableXlsx() throws Exception {
        byte[] content = new EduExcelReportRenderer().render(data);

        assertArrayEquals(new byte[]{'P', 'K'}, new byte[]{content[0], content[1]});
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("课程教学统计报表");
            assertNotNull(sheet);
            assertEquals("课程教学统计报表", sheet.getRow(0).getCell(0).getStringCellValue());
            boolean hasFirstTask = false;
            for (var row : sheet) {
                if (row.getCell(0) != null && "任务一".equals(row.getCell(0).getStringCellValue())) {
                    hasFirstTask = true;
                    break;
                }
            }
            assertTrue(hasFirstTask);
            assertTrue(sheet.getLastRowNum() > 60);
        }
    }

    @Test
    void pdfRendererEmbedsChineseAndPaginates() throws Exception {
        byte[] content = new EduPdfReportRenderer().render(data);
        Path output = Path.of("target", "test-reports", "teaching-report-sample.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, content);

        assertEquals("%PDF", new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(content)) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("课程教学统计报表"));
            assertTrue(text.contains("任务一"));
            assertTrue(text.contains("第 1 /"));
        }
    }

    @Test
    void pdfRendererDrawsEmDashPlaceholderForEmptyCells() throws Exception {
        TeachingReportData report = new TeachingReportData("个人训练报告", Map.of("学号", "20260001"), Map.of("达标率", "80.0%"),
                List.of("类型", "指标或日期", "达标率或综合分", "评价"), List.of(0.8F, 2F, 1.5F, 1F),
                List.of(List.of("能力", "教学设计", "80.0%", "优势"), List.of("趋势", "2026-10-06", "86", "—")));

        try (var document = Loader.loadPDF(new EduPdfReportRenderer().render(report))) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("能力"));
            assertTrue(text.contains("—"));
        }
    }

    private TeachingReportData sampleData() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("课程ID", "100");
        filters.put("学年", "2026-2027");
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("记录数", "64");
        summary.put("合格率", "82.50%");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 64; i++) {
            rows.add(List.of("任务" + chineseNumber(i), "82.5", "100", "60"));
        }
        return new TeachingReportData("课程教学统计报表", filters, summary,
                List.of("任务", "平均分", "最高分", "最低分"),
                List.of(3F, 1F, 1F, 1F), rows);
    }

    private String chineseNumber(int value) {
        return value == 1 ? "一" : String.valueOf(value);
    }
}
