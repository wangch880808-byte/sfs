package cn.nus.zknu.module.edu.controller.admin.classcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 教学班运行摘要响应 VO（P2-4 教学过程分析补教学班视角）
 */
@Schema(description = "教学班运行摘要响应")
@Data
public class ClassCourseProcessSummaryRespVO {

    @Schema(description = "在班学生数")
    private Long studentCount;

    @Schema(description = "任课教师数")
    private Long teacherCount;

    @Schema(description = "课堂数（班课关联状态）")
    private Integer bindingStatus;

    @Schema(description = "作业任务数")
    private Long homeworkCount;

    @Schema(description = "已提交数")
    private Long submittedCount;

    @Schema(description = "待批改数")
    private Long pendingGradingCount;

    @Schema(description = "平均分")
    private Double averageScore;

    @Schema(description = "作业完成表")
    private List<AssignmentStat> assignments;

    @Schema(description = "作业统计")
    @Data
    public static class AssignmentStat {
        @Schema(description = "作业 ID")
        private Long homeworkId;
        @Schema(description = "作业名称")
        private String homeworkName;
        @Schema(description = "截止时间")
        private String deadline;
        @Schema(description = "状态")
        private Integer status;
        @Schema(description = "应交人数")
        private Long expectedCount;
        @Schema(description = "已交人数")
        private Long submittedCount;
        @Schema(description = "已批改人数")
        private Long gradedCount;
        @Schema(description = "平均分")
        private Double averageScore;
        @Schema(description = "完成率（百分比）")
        private Double completionRate;
    }
}
