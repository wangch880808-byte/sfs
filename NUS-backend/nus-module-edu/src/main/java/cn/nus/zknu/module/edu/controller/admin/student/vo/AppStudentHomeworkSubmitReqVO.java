package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "学生端 - 提交任务 Request VO")
@Data
public class AppStudentHomeworkSubmitReqVO {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务编号不能为空")
    private Long homeworkId;

    @Schema(description = "提交内容", example = "这是我的任务内容...")
    private String content;

    @Schema(description = "附件URL列表（JSON数组）", example = "[\"https://oss.example.com/file1.pdf\"]")
    private String attachmentUrls;

}
