package cn.nus.zknu.module.edu.service.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Component
public class EduExcelReportRenderer {

    public byte[] render(TeachingReportData data) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(safeSheetName(data.title()));
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle labelStyle = labelStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(data.title());
            titleCell.setCellStyle(titleStyle);

            rowIndex = appendPairs(sheet, rowIndex, "筛选条件", data.filters(), labelStyle, bodyStyle);
            rowIndex = appendPairs(sheet, rowIndex, "统计摘要", data.summary(), labelStyle, bodyStyle);
            rowIndex++;

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < data.headers().size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(data.headers().get(i));
                cell.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, rowIndex);

            for (var values : data.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < data.headers().size(); i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(i < values.size() ? values.get(i) : "");
                    cell.setCellStyle(bodyStyle);
                }
            }
            for (int i = 0; i < data.headers().size(); i++) {
                float weight = i < data.columnWeights().size() ? data.columnWeights().get(i) : 1F;
                sheet.setColumnWidth(i, Math.min(255 * 256, Math.max(10, Math.round(weight * 14)) * 256));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private int appendPairs(Sheet sheet, int rowIndex, String section, Map<String, String> pairs,
                            CellStyle labelStyle, CellStyle bodyStyle) {
        if (pairs.isEmpty()) {
            return rowIndex;
        }
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            Cell label = row.createCell(0);
            label.setCellValue(section.isEmpty() ? entry.getKey() : section + " - " + entry.getKey());
            label.setCellStyle(labelStyle);
            Cell value = row.createCell(1);
            value.setCellValue(entry.getValue());
            value.setCellStyle(bodyStyle);
            section = "";
        }
        return rowIndex;
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }

    private CellStyle labelStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = labelStyle(workbook);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    private String safeSheetName(String title) {
        String value = WorkbookUtil.createSafeSheetName(title);
        return value.length() > 31 ? value.substring(0, 31) : value;
    }
}
