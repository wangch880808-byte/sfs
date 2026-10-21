package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "学生端 - 更新任务 Request VO")
@Data
public class AppStudentHomeworkUpdateReqVO {

    @Schema(description = "记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "记录编号不能为空")
    private Long id;

    @Schema(description = "提交内容", example = "这是我修改后的任务内容...")
    private String content;

    @Schema(description = "附件URL列表（JSON数组）", example = "[\"https://oss.example.com/file1.pdf\"]")
    private String attachmentUrls;

}
