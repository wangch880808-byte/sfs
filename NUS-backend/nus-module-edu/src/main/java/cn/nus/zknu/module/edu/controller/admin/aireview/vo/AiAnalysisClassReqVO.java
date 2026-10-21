package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "AI班级分析请求")
@Data
public class AiAnalysisClassReqVO {

    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "班级编号不能为空")
    private Long classId;

    @Schema(description = "课程ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程编号不能为空")
    private Long courseId;

    @Schema(description = "实训任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "实训任务编号不能为空")
    private Long homeworkId;

}
