package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 实训任务新增/修改 Request VO")
@Data
public class EduHomeworkSaveReqVO {

    @Schema(description = "任务 ID（更新时必填）", example = "1024")
    private Long id;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "实训任务一")
    @NotBlank(message = "任务名称不能为空")
    private String homeworkName;

    @Schema(description = "任务类型：1=平时任务, 2=考核任务", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务类型不能为空")
    private Integer homeworkType;

    @Schema(description = "任务等级：0=专业, 1=初级, 2=中级, 3=高级", example = "1")
    private Integer homeworkLevel;

    @Schema(description = "所属课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "所属班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "班级 ID 不能为空")
    private Long classId;

    @Schema(description = "所选班课关系 ID", example = "10")
    private Long classCourseId;

    @Schema(description = "复制来源任务 ID", example = "20")
    private Long sourceHomeworkId;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String semesterName;

    @Schema(description = "一级技能指标 ID", example = "1")
    private Long indicatorFirstId;

    @Schema(description = "二级技能指标 ID", example = "1")
    private Long indicatorSecondId;

    @Schema(description = "任务要求（富文本）", example = "请完成以下实训内容...")
    private String requirement;

    @Schema(description = "截止时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-04-01 23:59:59")
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;

    @Schema(description = "是否自动批改：0=手动, 1=自动AI批改", example = "0")
    private Integer autoGrade;

    @Schema(description = "提交模式：text/image/audio/video/mixed", example = "mixed")
    private String submissionMode;

    @Schema(description = "AI 评阅模式：0=仅人工, 1=自动初评+人工复核", example = "1")
    private Integer aiReviewMode;

    @Schema(description = "允许/要求的附件类型配置（JSON数组）", example = "[\"image\"]")
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

    @Schema(description = "状态：新建只允许 1=已发布，旧草稿保存即发布", example = "1")
    private Integer status;

}
