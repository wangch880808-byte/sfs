package cn.nus.zknu.module.edu.controller.admin.importbatch.vo;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EduImportErrorExcelVO {

    @ExcelProperty("工作表")
    @ColumnWidth(18)
    private String sheetName;

    @ExcelProperty("行号")
    @ColumnWidth(10)
    private Integer rowNumber;

    @ExcelProperty("业务键")
    @ColumnWidth(36)
    private String businessKey;

    @ExcelProperty("错误原因")
    @ColumnWidth(60)
    private String errors;

}
