package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "AI评分反馈请求")
@Data
public class AiReviewFeedbackReqVO {

    @Schema(description = "任务记录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务记录编号不能为空")
    private Long homeworkRecordId;

    @Schema(description = "反馈类型：strict/normal/loose", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "反馈类型不能为空")
    private String feedbackType;

}
