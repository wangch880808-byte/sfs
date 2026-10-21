package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - AI新建任务确认创建 Request VO")
@Data
public class EduHomeworkAiCreateConfirmReqVO {

    @Schema(description = "预览编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "预览编号不能为空")
    private String previewId;

    @Schema(description = "确认题目列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotEmpty(message = "确认题目不能为空")
    private List<Item> items;

    @Data
    public static class Item {

        @Schema(description = "预览题目编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "预览题目编号不能为空")
        private String previewItemId;

        @Schema(description = "最终任务标题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "任务标题不能为空")
        private String homeworkName;

        @Schema(description = "最终任务要求", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "任务要求不能为空")
        private String requirement;
    }
}
