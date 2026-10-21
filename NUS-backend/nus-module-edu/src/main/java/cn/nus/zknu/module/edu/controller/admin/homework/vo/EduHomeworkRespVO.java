package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 实训任务 Response VO")
@Data
public class EduHomeworkRespVO {

    @Schema(description = "当前用户是否可批改该任务")
    private Boolean canGrade;

    @Schema(description = "任务 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "实训任务一")
    private String homeworkName;

    @Schema(description = "任务类型：1=平时任务, 2=考核任务", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer homeworkType;

    @Schema(description = "任务等级：0=专业, 1=初级, 2=中级, 3=高级", example = "1")
    private Integer homeworkLevel;

    @Schema(description = "所属课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long courseId;

    @Schema(description = "所属班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long classId;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String semesterName;

    @Schema(description = "一级技能指标 ID", example = "1")
    private Long indicatorFirstId;

    @Schema(description = "二级技能指标 ID", example = "1")
    private Long indicatorSecondId;

    @Schema(description = "任务要求（富文本）", example = "请完成以下实训内容...")
    private String requirement;

    @Schema(description = "截止时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime deadline;

    @Schema(description = "是否自动批改：0=手动, 1=自动AI批改", example = "0")
    private Integer autoGrade;

    @Schema(description = "提交模式：text/image/audio/video/mixed", example = "mixed")
    private String submissionMode;

    @Schema(description = "AI 评阅模式：0=仅人工, 1=自动初评+人工复核", example = "1")
    private Integer aiReviewMode;

    @Schema(description = "允许/要求的附件类型配置（JSON数组）")
    private String requiredAttachmentTypesJson;

    @Schema(description = "AI 评分 rubric（JSON）")
    private String aiRubricJson;

    @Schema(description = "AI 参考资料（JSON）")
    private String aiReferenceJson;

    @Schema(description = "AI 初评置信度阈值", example = "0.7")
    private Double aiConfidenceThreshold;

    @Schema(description = "及格分数线", example = "60")
    private Integer passScore;

    @Schema(description = "满分值", example = "100")
    private Integer score;

    @Schema(description = "发布教师 ID", example = "1")
    private Long teacherId;

    @Schema(description = "状态：0=草稿, 1=已发布, 2=已截止, 3=已封存", example = "1")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
