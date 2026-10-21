package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "学生端 - 成绩趋势 Response VO")
@Data
public class AppStudentReportTrendRespVO {

    @Schema(description = "成绩趋势列表")
    private List<TrendItem> trends;

    @Schema(description = "趋势项")
    @Data
    public static class TrendItem {
        @Schema(description = "记录日期")
        private LocalDate recordDate;
        @Schema(description = "综合得分")
        private BigDecimal totalScore;
    }

}
