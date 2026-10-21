package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - AI新建任务预览 Request VO")
@Data
public class EduHomeworkAiCreatePreviewReqVO {

    @Schema(description = "客户端幂等请求编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "客户端请求编号不能为空")
    private String clientRequestId;

    @Schema(description = "所属课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "指定班级 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "指定班级不能为空")
    private List<Long> classIds;

    @Schema(description = "二级技能指标 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "二级能力指标不能为空")
    private Long indicatorSecondId;

    @Schema(description = "抽题数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "题目数量不能为空")
    @Min(value = 1, message = "题目数量至少为 1")
    @Max(value = 10, message = "单次最多生成 10 道题目")
    private Integer questionCount;

    @Schema(description = "任务类型：1=平时任务, 2=考核任务", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务类型不能为空")
    private Integer homeworkType;

    @Schema(description = "任务等级：0=专业, 1=初级, 2=中级, 3=高级", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务等级不能为空")
    private Integer homeworkLevel;

    @Schema(description = "截止时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-04-15 23:59:59")
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;

    @Schema(description = "是否自动批改：0=手动, 1=自动AI批改", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "批改方式不能为空")
    private Integer autoGrade;

    @Schema(description = "提交模式：text/image/audio/video/mixed", requiredMode = Schema.RequiredMode.REQUIRED, example = "text")
    @NotBlank(message = "提交模式不能为空")
    private String submissionMode;

    @Schema(description = "AI 评阅模式：0=仅人工, 1=自动初评+人工复核", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "AI 初评模式不能为空")
    private Integer aiReviewMode;

    @Schema(description = "及格分数线", requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
    @NotNull(message = "及格分数线不能为空")
    @Min(value = 0, message = "及格分数线不能小于 0")
    private Integer passScore;

    @Schema(description = "满分值", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "满分值不能为空")
    @Min(value = 1, message = "满分值至少为 1")
    private Integer score;

}
