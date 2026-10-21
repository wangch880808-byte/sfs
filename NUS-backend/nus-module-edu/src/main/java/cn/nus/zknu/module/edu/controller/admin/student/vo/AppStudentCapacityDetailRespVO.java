package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "学生端 - 指标详情 Response VO")
@Data
public class AppStudentCapacityDetailRespVO {

    @Schema(description = "一级指标ID", example = "1")
    private Long indicatorFirstId;

    @Schema(description = "一级指标名称", example = "教学设计能力")
    private String indicatorFirstName;

    @Schema(description = "二级指标列表")
    private List<SecondIndicator> secondIndicators;

    @Schema(description = "二级指标")
    @Data
    public static class SecondIndicator {
        @Schema(description = "二级指标ID", example = "10")
        private Long indicatorSecondId;
        @Schema(description = "二级指标名称", example = "教案编写")
        private String indicatorName;
        @Schema(description = "总任务数", example = "5")
        private Integer totalTasks;
        private Integer submittedTasks;
        private Integer pendingReviewTasks;
        private Integer evaluatedTasks;
        @Schema(description = "达标任务数", example = "3")
        private Integer qualifiedTasks;
        @Schema(description = "达标率（百分比）", example = "60.0")
        private Double qualifiedRate;
        private String evaluationStatus;
    }

    @Schema(description = "相关任务列表")
    private List<HomeworkItem> homeworks;

    @Schema(description = "相关任务")
    @Data
    public static class HomeworkItem {
        @Schema(description = "任务ID", example = "1")
        private Long homeworkId;
        @Schema(description = "任务名称", example = "教案编写练习")
        private String homeworkName;
        @Schema(description = "二级指标ID", example = "10")
        private Long indicatorSecondId;
        @Schema(description = "得分", example = "85")
        private Integer score;
        @Schema(description = "是否达标", example = "true")
        private Boolean qualified;
        private Integer recordStatus;
    }

}
