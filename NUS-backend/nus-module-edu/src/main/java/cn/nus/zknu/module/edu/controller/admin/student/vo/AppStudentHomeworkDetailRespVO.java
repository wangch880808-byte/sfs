package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "学生端 - 任务详情 Response VO")
@Data
public class AppStudentHomeworkDetailRespVO {

    @Schema(description = "任务ID", example = "1")
    private Long homeworkId;

    @Schema(description = "任务名称", example = "普通话朗读训练")
    private String homeworkName;

    @Schema(description = "任务类型：1=平时任务, 2=考核任务", example = "1")
    private Integer homeworkType;

    @Schema(description = "任务等级：0=专业, 1=初级, 2=中级, 3=高级", example = "1")
    private Integer homeworkLevel;

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "课程名称", example = "普通话实训")
    private String courseName;

    @Schema(description = "任务要求（富文本）")
    private String requirement;

    @Schema(description = "提交模式：text/image/audio/video/mixed", example = "mixed")
    private String submissionMode;

    @Schema(description = "AI 评阅模式：0=仅人工, 1=自动初评+人工复核", example = "1")
    private Integer aiReviewMode;

    @Schema(description = "允许/要求的附件类型配置（JSON数组）")
    private String requiredAttachmentTypesJson;

    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    @Schema(description = "满分值", example = "100")
    private Integer score;

    @Schema(description = "及格分数线", example = "60")
    private Integer passScore;

    @Schema(description = "提交记录ID")
    private Long recordId;

    @Schema(description = "提交内容")
    private String content;

    @Schema(description = "附件URL列表（JSON数组）")
    private String attachmentUrls;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "得分", example = "85")
    private Integer myScore;

    @Schema(description = "教师/AI评语")
    private String comment;

    @Schema(description = "批改方式：1=手动, 2=AI", example = "1")
    private Integer correctType;

    @Schema(description = "批改时间")
    private LocalDateTime correctTime;

    @Schema(description = "提交状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交", example = "0")
    private Integer status;

    @Schema(description = "累计打回重做次数", example = "1")
    private Integer redoCount;

    @Schema(description = "重做截止时间，为空时沿用任务截止时间")
    private LocalDateTime redoDeadline;

    @Schema(description = "个人截止时间：教师为该学生设置的个别延期，晚于任务截止时间时才返回，否则为空")
    private LocalDateTime personalDeadline;

    @Schema(description = "实际截止时间：已打回或设置了重做截止时间时按重做规则，否则取任务截止时间与个人截止时间中较晚的一个；为空表示不限")
    private LocalDateTime effectiveDeadline;

    @Schema(description = "当前是否允许提交或重新提交", example = "true")
    private Boolean canSubmit;

    @Schema(description = "是否往期任务（只读）：班级、课程或班课已封存，或任务已封存", example = "false")
    private Boolean historyOnly;
    @Schema(description = "附件上限与允许的类型（提交页在上传前校验并写进提示）")
    private AttachmentLimits attachmentLimits;

    @Schema(description = "学生端 - 提交附件上限")
    @Data
    public static class AttachmentLimits {

        @Schema(description = "每次提交最多几个附件", example = "10")
        private Integer maxCount;

        @Schema(description = "单个附件上限（MB）", example = "100")
        private Integer maxFileSizeMb;

        @Schema(description = "附件合计上限（MB）", example = "300")
        private Integer maxTotalSizeMb;

        @Schema(description = "允许的附件类型：image/audio/video；为空表示不限", example = "[\"image\",\"audio\"]")
        private List<String> allowedTypes;

    }

}
