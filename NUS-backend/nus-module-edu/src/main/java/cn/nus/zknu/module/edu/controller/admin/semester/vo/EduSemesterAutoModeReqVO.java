package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 学期自动更新模式 Request VO")
@Data
public class EduSemesterAutoModeReqVO {

    @Schema(description = "是否自动更新学期", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "自动更新状态不能为空")
    private Boolean autoEnabled;
}
