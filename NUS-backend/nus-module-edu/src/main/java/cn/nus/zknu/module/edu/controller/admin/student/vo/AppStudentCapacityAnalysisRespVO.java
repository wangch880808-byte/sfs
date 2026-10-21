package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "学生端 - 能力分析概览 Response VO")
@Data
public class AppStudentCapacityAnalysisRespVO {

    @Schema(description = "指标达标率列表（雷达图数据）")
    private List<IndicatorRate> indicators;

    @Schema(description = "关联一级指标的已评定任务的加权综合达标率（百分比）", example = "72.5")
    private Double overallRate;

    @Schema(description = "综合达标率是否达到证书达标率阈值；无已评定指标时为空", example = "true")
    private Boolean qualified;

    @Schema(description = "能力画像评定状态；无已评定指标时为 PENDING")
    private String evaluationStatus;
    @Schema(description = "全部历史可见任务数（含未关联一级指标的任务）")
    private Integer totalTasks;
    private Integer submittedTasks;
    private Integer pendingReviewTasks;
    private Integer evaluatedTasks;
    private Integer qualifiedTasks;

    @Schema(description = "已评定但未关联一级指标的任务数，不计入能力画像", example = "1")
    private Integer unindicatedEvaluatedCount;

    @Schema(description = "指标达标率")
    @Data
    public static class IndicatorRate {
        @Schema(description = "一级指标ID", example = "1")
        private Long indicatorFirstId;
        @Schema(description = "指标名称", example = "教学设计能力")
        private String indicatorName;
        @Schema(description = "总任务数", example = "10")
        private Integer totalTasks;
        private Integer submittedTasks;
        private Integer pendingReviewTasks;
        private Integer evaluatedTasks;
        @Schema(description = "达标任务数", example = "7")
        private Integer qualifiedTasks;
        @Schema(description = "达标率（百分比）", example = "70.0")
        private Double qualifiedRate;
        private String evaluationStatus;
    }

}
