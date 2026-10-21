package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "学生端 - 技能分析 Response VO")
@Data
public class AppStudentReportSkillsRespVO {

    @Schema(description = "技能指标列表")
    private List<SkillItem> skills;

    @Schema(description = "优势指标名称列表")
    private List<String> strengths;

    @Schema(description = "薄弱指标名称列表")
    private List<String> weaknesses;

    @Schema(description = "技能项")
    @Data
    public static class SkillItem {
        @Schema(description = "指标名称", example = "教学设计能力")
        private String indicatorName;
        @Schema(description = "达标率（百分比）", example = "85.0")
        private Double qualifiedRate;
        private Integer totalTasks;
        private Integer submittedTasks;
        private Integer pendingReviewTasks;
        private Integer evaluatedTasks;
        private Integer qualifiedTasks;
        private String evaluationStatus;
    }

}
