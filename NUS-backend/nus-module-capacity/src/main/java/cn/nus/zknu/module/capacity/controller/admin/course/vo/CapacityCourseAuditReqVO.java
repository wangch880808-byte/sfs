package cn.nus.zknu.module.capacity.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 实训课程公共课审批 Request VO")
@Data
public class CapacityCourseAuditReqVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "课程 ID 不能为空")
    private Long id;

    @Schema(description = "是否通过审批", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    @Schema(description = "审批意见", example = "请补充课程封面和简介后重新提交")
    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    private String remark;

}
