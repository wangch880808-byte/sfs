package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "AI全部批改请求")
@Data
public class AiReviewCorrectAllReqVO {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务编号不能为空")
    private Long homeworkId;

    @Schema(description = "班级ID")
    private Long classId;

}
