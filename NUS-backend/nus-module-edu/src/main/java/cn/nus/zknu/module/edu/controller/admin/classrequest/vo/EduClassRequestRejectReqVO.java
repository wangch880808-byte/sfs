package cn.nus.zknu.module.edu.controller.admin.classrequest.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 建班申请拒绝 Request VO")
@Data
public class EduClassRequestRejectReqVO {

    @Schema(description = "拒绝原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "班级人数不足")
    @NotBlank(message = "拒绝原因不能为空")
    private String reason;

}
