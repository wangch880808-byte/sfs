package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "学生端 - 任务列表项 Response VO")
@Data
public class AppStudentHomeworkRespVO {

    @Schema(description = "任务ID", example = "1")
    private Long homeworkId;

    @Schema(description = "任务名称", example = "普通话朗读训练")
    private String homeworkName;

    @Schema(description = "任务类型：1=平时任务, 2=考核任务", example = "1")
    private Integer homeworkType;

    @Schema(description = "任务等级：0=专业, 1=初级, 2=中级, 3=高级", example = "1")
    private Integer homeworkLevel;

    @Schema(description = "一级指标（技能）编号，0=未填指标；闯关按课程+指标分组", example = "6")
    private Long indicatorFirstId;

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "课程名称", example = "普通话实训")
    private String courseName;

    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    @Schema(description = "满分值", example = "100")
    private Integer score;

    @Schema(description = "及格分数线", example = "60")
    private Integer passScore;

    @Schema(description = "提交记录ID（未提交则为空）")
    private Long recordId;

    @Schema(description = "提交状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交", example = "0")
    private Integer status;

    @Schema(description = "得分", example = "85")
    private Integer myScore;

    @Schema(description = "重做截止时间，为空时沿用任务截止时间")
    private LocalDateTime redoDeadline;

    @Schema(description = "个人截止时间：教师为该学生设置的个别延期，晚于任务截止时间时才返回，否则为空")
    private LocalDateTime personalDeadline;

    @Schema(description = "实际截止时间：已打回或设置了重做截止时间时按重做规则，否则取任务截止时间与个人截止时间中较晚的一个；为空表示不限")
    private LocalDateTime effectiveDeadline;
    @Schema(description = "是否往期任务（只读）", example = "false")
    private Boolean historyOnly;

    @Schema(description = "任务所在等级是否尚未解锁", example = "true")
    private Boolean locked;

    @Schema(description = "未解锁原因，与课程 unlock-status 的 reasonText 一致；未锁定时为空")
    private String lockReason;

}
