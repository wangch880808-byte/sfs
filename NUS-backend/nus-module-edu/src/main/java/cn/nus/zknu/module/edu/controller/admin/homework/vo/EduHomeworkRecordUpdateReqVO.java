package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 任务更新 Request VO")
@Data
public class EduHomeworkRecordUpdateReqVO {

    @Schema(description = "记录 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "记录 ID 不能为空")
    private Long id;

    @Schema(description = "提交内容", example = "这是我更新后的任务内容...")
    private String content;

    @Schema(description = "附件URL列表（JSON数组）", example = "[\"https://oss.example.com/file1.pdf\"]")
    private String attachmentUrls;

}
