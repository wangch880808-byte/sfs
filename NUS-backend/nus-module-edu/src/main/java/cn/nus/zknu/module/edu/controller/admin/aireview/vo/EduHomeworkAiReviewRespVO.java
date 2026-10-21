package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 作业AI初评草案响应")
public class EduHomeworkAiReviewRespVO {

    private Long id;

    private Long homeworkRecordId;

    private Integer status;

    private String submissionMode;

    private Integer aiScore;

    private String aiComment;

    @Schema(description = "学生可见评语建议")
    private String studentCommentSuggestion;

    private Double aiConfidence;

    @Schema(description = "该任务配置的人工复核置信度阈值")
    private Double confidenceThreshold;

    private String transcript;

    private String evidenceJson;

    private String subScoresJson;

    private String riskFlagsJson;

    private String teacherDecision;

    private Integer teacherScore;

    private String teacherComment;

    private String errorMessage;
}
