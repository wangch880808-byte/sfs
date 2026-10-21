package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - AI新建任务单题重新生成 Request VO")
@Data
public class EduHomeworkAiCreateRegenerateReqVO {

    @Schema(description = "预览编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "预览编号不能为空")
    private String previewId;

    @Schema(description = "预览题目编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "预览题目编号不能为空")
    private String previewItemId;

}
