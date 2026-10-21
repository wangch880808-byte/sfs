package cn.nus.zknu.module.edu.controller.admin.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "学生发送留言请求")
@Data
public class MessageStudentAddReqVO {

    @Schema(description = "班级ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "班级编号不能为空")
    private Long classId;

    @Schema(description = "课程ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程编号不能为空")
    private Long courseId;

    @Schema(description = "留言内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 1000, message = "留言内容不能超过 1000 字")
    private String content;

}
