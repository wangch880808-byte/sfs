package cn.nus.zknu.module.edu.controller.admin.courseresource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 课程资源审核 Request VO")
@Data
public class EduCourseResourceReviewReqVO {

    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Schema(description = "审核原因（驳回时必填）", example = "内容不合规")
    private String reason;

}
