package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "学生端 - 首页概览 Response VO")
@Data
public class AppStudentHomeOverviewRespVO {

    @Schema(description = "待完成任务数", example = "5")
    private Integer pendingTaskCount;

    @Schema(description = "待完成任务数（前端兼容字段）", example = "5")
    private Integer pendingCount;

    @Schema(description = "已提交任务数", example = "3")
    private Integer submittedCount;

    @Schema(description = "平均分", example = "88.5")
    private Double averageScore;

    @Schema(description = "近期成绩列表")
    private List<RecentScore> recentScores;

    @Schema(description = "近期成绩")
    @Data
    public static class RecentScore {
        @Schema(description = "任务ID", example = "1")
        private Long homeworkId;
        @Schema(description = "任务名称", example = "普通话朗读训练")
        private String homeworkName;
        @Schema(description = "得分", example = "85")
        private Integer score;
        @Schema(description = "状态", example = "2")
        private Integer status;
        @Schema(description = "批改时间")
        private java.time.LocalDateTime correctTime;
    }

}
