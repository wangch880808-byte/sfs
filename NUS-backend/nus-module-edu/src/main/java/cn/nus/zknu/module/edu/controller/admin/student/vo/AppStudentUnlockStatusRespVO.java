package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "学生端 - 等级解锁状态 Response VO")
@Data
public class AppStudentUnlockStatusRespVO {

    @Schema(description = "各等级解锁状态")
    private List<Level> levels;

    @Schema(description = "课程已封存，只能查看", example = "false")
    private Boolean archived;

    /**
     * 每个等级的计数描述该等级自己的任务；等级 L 是否解锁看上一等级的达标比例是否达到 threshold，
     * 没有解锁时 reasonText 用一句话说明还差什么。
     */
    @Data
    public static class Level {
        @Schema(description = "等级：1=初级，2=中级，3=高级", example = "2")
        private Integer level;
        @Schema(description = "一级指标（技能）编号，0=未填指标", example = "6")
        private Long indicatorFirstId;
        @Schema(description = "等级名称（带技能名，如「表达技能 · 中级」）", example = "表达技能 · 中级")
        private String levelName;
        @Schema(description = "分配给学生的该等级任务数", example = "5")
        private Integer totalTasks;
        @Schema(description = "达标任务数（同 passedCount，保留兼容）", example = "3")
        private Integer qualifiedTasks;
        @Schema(description = "达标率（百分数，passedCount / requiredCount），分母为 0 时为 0", example = "75.0")
        private Double qualificationRate;
        @Schema(description = "是否已解锁", example = "true")
        private Boolean unlocked;
        @Schema(description = "解锁时间（快照）；初级、或上一等级没有任务直接放行时为空")
        private LocalDateTime unlockedAt;
        @Schema(description = "达标任务数：最近一次批改的分数达到达标分数线", example = "3")
        private Integer passedCount;
        @Schema(description = "计入的任务数：已过截止时间或已提交", example = "4")
        private Integer requiredCount;
        @Schema(description = "在等教师批改的任务数", example = "1")
        private Integer pendingReviewCount;
        @Schema(description = "达标比例（0~1），计入的任务数为 0 时为空", example = "0.75")
        private Double ratio;
        @Schema(description = "解锁下一等级需要的达标比例（0~1）", example = "0.8")
        private Double threshold;
        @Schema(description = "没有解锁的原因（一句话，60 字以内）；已解锁时为空", example = "初级还差 2 个任务达标（需达标 80%）")
        private String reasonText;
    }

}
