package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "AI批改单份请求")
@Data
public class AiReviewCorrectReqVO {

    @Schema(description = "任务记录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务记录编号不能为空")
    private Long homeworkRecordId;

}
