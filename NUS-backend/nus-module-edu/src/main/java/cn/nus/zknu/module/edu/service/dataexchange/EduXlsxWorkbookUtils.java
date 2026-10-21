package cn.nus.zknu.module.edu.service.dataexchange;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID;

public final class EduXlsxWorkbookUtils {

    private static final int VALIDATION_LAST_ROW = 1000;

    /**
     * V1.0.1.6 按界面术语表改过的表头：旧表头 → 现表头。改名前下载的模板仍按原位置导入，读出的数据以现表头为键。
     */
    private static final Map<String, String> LEGACY_HEADERS = Map.of(
            "一级技能指标*", "一级能力指标*",
            "二级技能指标*", "二级能力指标*",
            "技能等级*", "任务等级*",
            "班级ID", "班级编号",
            "班级ID*", "班级编号*");

    public record SheetSpec(String name, List<String> headers, Set<Integer> textColumns, List<String> exampleRow) {
        public SheetSpec(String name, List<String> headers, Set<Integer> textColumns) {
            this(name, headers, textColumns, List.of());
        }

        public SheetSpec {
            if (!exampleRow.isEmpty() && exampleRow.size() != headers.size()) {
                throw new IllegalArgumentException("exampleRow size must match headers");
            }
        }
    }

    public record DictionaryEntry(String field, String value, String description) {
    }

    public record DependentDropdownSpec(String parentHeader, String childHeader,
                                        Map<String, List<String>> optionsByParent) {
    }

    public record RowData(int rowNumber, Map<String, String> values) {
        public String get(String header) {
            return values.get(header);
        }
    }

    private EduXlsxWorkbookUtils() {
    }

    public static byte[] createTemplate(String bizType, String version, List<String> rules,
                                        List<SheetSpec> sheets, List<DictionaryEntry> dictionaries) {
        return createTemplate(bizType, version, rules, sheets, dictionaries, Map.of(), List.of());
    }

    public static byte[] createTemplate(String bizType, String version, List<String> rules,
                                        List<SheetSpec> sheets, List<DictionaryEntry> dictionaries,
                                        Map<String, List<String>> dropdownOptions) {
        return createTemplate(bizType, version, rules, sheets, dictionaries, dropdownOptions, List.of());
    }

    public static byte[] createTemplate(String bizType, String version, List<String> rules,
                                        List<SheetSpec> sheets, List<DictionaryEntry> dictionaries,
                                        Map<String, List<String>> dropdownOptions,
                                        List<DependentDropdownSpec> dependentDropdowns) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            CellStyle inputStyle = inputStyle(workbook, null);
            CellStyle textInputStyle = inputStyle(workbook, textStyle);

            Sheet info = workbook.createSheet("模板信息");
            writeRow(info, 0, List.of("项目", "内容"), headerStyle);
            writeRow(info, 1, List.of("业务类型", bizType), null);
            writeRow(info, 2, List.of("模板版本", version), null);
            writeRow(info, 3, List.of("文件格式", ".xlsx"), null);
            int ruleRow = 4;
            for (String rule : rules) {
                writeRow(info, ruleRow++, List.of("导入规则", rule), null);
            }
            info.setColumnWidth(0, 18 * 256);
            info.setColumnWidth(1, 80 * 256);
            info.protectSheet("nus-template");

            Map<String, String> dropdownNames = createDropdownSheet(workbook, dropdownOptions);
            List<DependentValidation> dependentValidations = createDependentDropdownSheet(
                    workbook, dependentDropdowns);

            for (SheetSpec spec : sheets) {
                Sheet sheet = workbook.createSheet(spec.name());
                Row header = sheet.createRow(0);
                for (int column = 0; column < spec.headers().size(); column++) {
                    Cell cell = header.createCell(column, CellType.STRING);
                    cell.setCellValue(spec.headers().get(column));
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(column, columnWidth(spec.headers().get(column)) * 256);
                    if (spec.textColumns().contains(column)) {
                        sheet.setDefaultColumnStyle(column, textStyle);
                    }
                }
                header.setHeightInPoints(24);
                createInputHintRow(sheet, spec, inputStyle, textInputStyle);
                addValidations(sheet, spec.headers(), dropdownNames);
                addDependentValidations(sheet, spec.headers(), dependentValidations);
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0,
                        spec.headers().size() - 1));
            }

            createExampleSheet(workbook, sheets, headerStyle, inputStyle, textInputStyle);

            Sheet dict = workbook.createSheet("数据字典");
            writeRow(dict, 0, List.of("字段", "可用值", "说明"), titleStyle);
            int rowIndex = 1;
            for (DictionaryEntry entry : dictionaries) {
                writeRow(dict, rowIndex++, List.of(entry.field(), entry.value(), entry.description()), null);
            }
            dict.setColumnWidth(0, 24 * 256);
            dict.setColumnWidth(1, 30 * 256);
            dict.setColumnWidth(2, 60 * 256);
            workbook.setSheetHidden(workbook.getSheetIndex(dict), true);
            int activeSheetIndex = workbook.getSheetIndex(sheets.get(0).name());
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                workbook.getSheetAt(sheetIndex).setSelected(sheetIndex == activeSheetIndex);
            }
            workbook.setActiveSheet(activeSheetIndex);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    public static XSSFWorkbook open(byte[] content) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(content));
        } catch (Exception ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    public static List<RowData> readSheet(Workbook workbook, String sheetName, List<String> expectedHeaders) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (int column = 0; column < expectedHeaders.size(); column++) {
            String expected = expectedHeaders.get(column);
            String actual = format(formatter, evaluator, headerRow.getCell(column));
            if (!expected.equals(actual) && !expected.equals(LEGACY_HEADERS.get(actual))) {
                throw exception(IMPORT_PREVIEW_INVALID);
            }
        }
        List<RowData> rows = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int column = 0; column < expectedHeaders.size(); column++) {
                String value = format(formatter, evaluator, row.getCell(column)).trim();
                values.put(expectedHeaders.get(column), value);
                hasValue |= !value.isEmpty();
            }
            if (hasValue) {
                rows.add(new RowData(index + 1, values));
            }
        }
        return rows;
    }

    public static byte[] writeWorkbook(Workbook workbook) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    private static String format(DataFormatter formatter, FormulaEvaluator evaluator, Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    private static void writeRow(Sheet sheet, int rowIndex, List<String> values, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < values.size(); column++) {
            Cell cell = row.createCell(column, CellType.STRING);
            cell.setCellValue(values.get(column));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = titleStyle(workbook);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle inputStyle(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        if (baseStyle != null) {
            style.cloneStyleFrom(baseStyle);
        }
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    private static void createInputHintRow(Sheet sheet, SheetSpec spec, CellStyle inputStyle,
                                           CellStyle textInputStyle) {
        Row row = sheet.createRow(1);
        row.setHeightInPoints(22);
        for (int column = 0; column < spec.headers().size(); column++) {
            Cell cell = row.createCell(column, CellType.BLANK);
            cell.setCellStyle(spec.textColumns().contains(column) ? textInputStyle : inputStyle);
        }
    }

    private static void createExampleSheet(XSSFWorkbook workbook, List<SheetSpec> specs, CellStyle headerStyle,
                                           CellStyle inputStyle, CellStyle textInputStyle) {
        if (specs.stream().noneMatch(spec -> !spec.exampleRow().isEmpty())) {
            return;
        }
        Sheet example = workbook.createSheet("填写示例");
        int rowIndex = 0;
        int maxColumns = 0;
        for (SheetSpec spec : specs) {
            if (spec.exampleRow().isEmpty()) {
                continue;
            }
            Row title = example.createRow(rowIndex++);
            Cell titleCell = title.createCell(0, CellType.STRING);
            titleCell.setCellValue(spec.name() + "（仅供参考，请在对应业务工作表填写）");
            titleCell.setCellStyle(headerStyle);
            if (spec.headers().size() > 1) {
                example.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                        title.getRowNum(), title.getRowNum(), 0, spec.headers().size() - 1));
            }
            Row headers = example.createRow(rowIndex++);
            Row values = example.createRow(rowIndex++);
            for (int column = 0; column < spec.headers().size(); column++) {
                Cell header = headers.createCell(column, CellType.STRING);
                header.setCellValue(spec.headers().get(column));
                header.setCellStyle(headerStyle);
                Cell value = values.createCell(column, CellType.STRING);
                value.setCellValue(spec.exampleRow().get(column));
                value.setCellStyle(spec.textColumns().contains(column) ? textInputStyle : inputStyle);
                example.setColumnWidth(column, Math.max(example.getColumnWidth(column),
                        columnWidth(spec.headers().get(column)) * 256));
            }
            rowIndex++;
            maxColumns = Math.max(maxColumns, spec.headers().size());
        }
        if (maxColumns > 0) {
            example.createFreezePane(0, 1);
        }
    }

    private static Map<String, String> createDropdownSheet(XSSFWorkbook workbook,
                                                            Map<String, List<String>> dropdownOptions) {
        if (dropdownOptions.isEmpty()) {
            return Map.of();
        }
        Sheet optionsSheet = workbook.createSheet("下拉选项");
        Map<String, String> dropdownNames = new HashMap<>();
        int column = 0;
        for (Map.Entry<String, List<String>> entry : dropdownOptions.entrySet()) {
            List<String> values = entry.getValue().stream().filter(Objects::nonNull).distinct().toList();
            if (values.isEmpty()) {
                continue;
            }
            Row header = Optional.ofNullable(optionsSheet.getRow(0)).orElseGet(() -> optionsSheet.createRow(0));
            header.createCell(column, CellType.STRING).setCellValue(entry.getKey());
            for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
                Row row = optionsSheet.getRow(rowIndex + 1);
                if (row == null) {
                    row = optionsSheet.createRow(rowIndex + 1);
                }
                row.createCell(column, CellType.STRING).setCellValue(values.get(rowIndex));
            }
            String rangeName = "NUS_DROPDOWN_" + column;
            Name name = workbook.createName();
            name.setNameName(rangeName);
            name.setRefersToFormula("'下拉选项'!$" + columnName(column) + "$2:$"
                    + columnName(column) + "$" + (values.size() + 1));
            dropdownNames.put(normalizeHeader(entry.getKey()), rangeName);
            column++;
        }
        workbook.setSheetHidden(workbook.getSheetIndex(optionsSheet), true);
        return dropdownNames;
    }

    private static List<DependentValidation> createDependentDropdownSheet(
            XSSFWorkbook workbook, List<DependentDropdownSpec> dependentDropdowns) {
        if (dependentDropdowns.isEmpty()) {
            return List.of();
        }
        Sheet optionsSheet = workbook.createSheet("级联选项");
        List<DependentValidation> validations = new ArrayList<>();
        int column = 0;
        for (int specIndex = 0; specIndex < dependentDropdowns.size(); specIndex++) {
            DependentDropdownSpec spec = dependentDropdowns.get(specIndex);
            String emptyRangeName = "NUS_DEP_EMPTY_" + specIndex;
            writeOption(optionsSheet, 0, column, spec.childHeader() + "（空）");
            createName(workbook, emptyRangeName, optionsSheet.getSheetName(), column, 1, 1);
            column++;

            int mappingLabelColumn = column++;
            int mappingRangeColumn = column++;
            writeOption(optionsSheet, 0, mappingLabelColumn, spec.parentHeader());
            writeOption(optionsSheet, 0, mappingRangeColumn, "内部选项区域");
            int mappingRow = 1;
            int childIndex = 0;
            for (Map.Entry<String, List<String>> entry : spec.optionsByParent().entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<String> children = entry.getValue() == null ? List.of()
                        : entry.getValue().stream().filter(Objects::nonNull).distinct().toList();
                String childRangeName = emptyRangeName;
                if (!children.isEmpty()) {
                    int childColumn = column++;
                    childRangeName = "NUS_DEP_" + specIndex + "_" + childIndex++;
                    writeOption(optionsSheet, 0, childColumn, entry.getKey());
                    for (int rowIndex = 0; rowIndex < children.size(); rowIndex++) {
                        writeOption(optionsSheet, rowIndex + 1, childColumn, children.get(rowIndex));
                    }
                    createName(workbook, childRangeName, optionsSheet.getSheetName(), childColumn,
                            1, children.size());
                }
                writeOption(optionsSheet, mappingRow, mappingLabelColumn, entry.getKey());
                writeOption(optionsSheet, mappingRow, mappingRangeColumn, childRangeName);
                mappingRow++;
            }
            if (mappingRow == 1) {
                writeOption(optionsSheet, mappingRow, mappingLabelColumn, "");
                writeOption(optionsSheet, mappingRow, mappingRangeColumn, emptyRangeName);
                mappingRow++;
            }
            String mappingRangeName = "NUS_DEP_MAP_" + specIndex;
            Name mappingName = workbook.createName();
            mappingName.setNameName(mappingRangeName);
            mappingName.setRefersToFormula("'" + optionsSheet.getSheetName() + "'!$"
                    + columnName(mappingLabelColumn) + "$2:$" + columnName(mappingRangeColumn)
                    + "$" + mappingRow);
            validations.add(new DependentValidation(spec.parentHeader(), spec.childHeader(),
                    mappingRangeName, emptyRangeName));
        }
        workbook.setSheetHidden(workbook.getSheetIndex(optionsSheet), true);
        return validations;
    }

    private static void writeOption(Sheet sheet, int rowIndex, int columnIndex, String value) {
        Row row = Optional.ofNullable(sheet.getRow(rowIndex)).orElseGet(() -> sheet.createRow(rowIndex));
        row.createCell(columnIndex, CellType.STRING).setCellValue(value);
    }

    private static void createName(XSSFWorkbook workbook, String rangeName, String sheetName,
                                   int column, int firstRow, int rowCount) {
        Name name = workbook.createName();
        name.setNameName(rangeName);
        name.setRefersToFormula("'" + sheetName + "'!$" + columnName(column) + "$" + (firstRow + 1)
                + ":$" + columnName(column) + "$" + (firstRow + rowCount));
    }

    private static void addValidations(Sheet sheet, List<String> headers, Map<String, String> dropdownNames) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (int column = 0; column < headers.size(); column++) {
            String normalizedHeader = normalizeHeader(headers.get(column));
            String rangeName = dropdownNames.get(normalizedHeader);
            String[] values = validationValues(normalizedHeader);
            if (rangeName == null && values == null) {
                continue;
            }
            DataValidationConstraint constraint = rangeName == null
                    ? helper.createExplicitListConstraint(values)
                    : helper.createFormulaListConstraint(rangeName);
            org.apache.poi.ss.util.CellRangeAddressList range =
                    new org.apache.poi.ss.util.CellRangeAddressList(1, VALIDATION_LAST_ROW, column, column);
            DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            validation.setShowPromptBox(true);
            validation.createPromptBox("请选择", "请从下拉列表中选择有效值");
            validation.setSuppressDropDownArrow(true);
            sheet.addValidationData(validation);
        }
    }

    private static void addDependentValidations(Sheet sheet, List<String> headers,
                                                List<DependentValidation> dependentValidations) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (DependentValidation validationSpec : dependentValidations) {
            int parentColumn = findColumn(headers, validationSpec.parentHeader());
            int childColumn = findColumn(headers, validationSpec.childHeader());
            if (parentColumn < 0 || childColumn < 0) {
                continue;
            }
            String parentCell = "$" + columnName(parentColumn) + "2";
            String formula = "INDIRECT(IFERROR(VLOOKUP(" + parentCell + ","
                    + validationSpec.mappingRangeName() + ",2,FALSE),\""
                    + validationSpec.emptyRangeName() + "\"))";
            DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
            org.apache.poi.ss.util.CellRangeAddressList range =
                    new org.apache.poi.ss.util.CellRangeAddressList(1, VALIDATION_LAST_ROW,
                            childColumn, childColumn);
            DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            validation.setShowPromptBox(true);
            validation.createPromptBox("请先选择上级", "二级选项会根据同一行的一级选项联动更新");
            validation.setSuppressDropDownArrow(true);
            sheet.addValidationData(validation);
        }
    }

    private static int findColumn(List<String> headers, String expectedHeader) {
        String normalizedExpected = normalizeHeader(expectedHeader);
        for (int column = 0; column < headers.size(); column++) {
            if (Objects.equals(normalizeHeader(headers.get(column)), normalizedExpected)) {
                return column;
            }
        }
        return -1;
    }

    private record DependentValidation(String parentHeader, String childHeader,
                                       String mappingRangeName, String emptyRangeName) {
    }

    private static String[] validationValues(String header) {
        return switch (header) {
            case "组织类型" -> new String[]{"学校", "学院", "专业"};
            case "性别" -> new String[]{"未知", "男", "女"};
            case "状态" -> new String[]{"正常", "停用"};
            case "培养层次" -> new String[]{"专科", "本科", "硕士", "博士"};
            case "学制" -> new String[]{"2", "3", "4", "5"};
            default -> null;
        };
    }

    private static String normalizeHeader(String header) {
        return header.replace("*", "");
    }

    private static String columnName(int column) {
        StringBuilder result = new StringBuilder();
        int current = column;
        do {
            result.insert(0, (char) ('A' + current % 26));
            current = current / 26 - 1;
        } while (current >= 0);
        return result.toString();
    }

    private static int columnWidth(String header) {
        String normalized = header.replace("*", "");
        if (normalized.contains("评语") || normalized.contains("备注") || normalized.contains("原因")) return 38;
        if (normalized.contains("邮箱")) return 32;
        if (normalized.contains("编码") || normalized.contains("学号") || normalized.contains("令牌")
                || normalized.contains("校验值")) return 26;
        if (normalized.contains("名称") || normalized.equals("姓名") || normalized.equals("班级")) return 24;
        if (normalized.contains("手机") || normalized.contains("电话")) return 18;
        return Math.min(22, Math.max(14, normalized.length() * 3));
    }

}
