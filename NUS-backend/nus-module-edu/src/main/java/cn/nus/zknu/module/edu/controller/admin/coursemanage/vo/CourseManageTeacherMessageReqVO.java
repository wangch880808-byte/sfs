package cn.nus.zknu.module.edu.controller.admin.coursemanage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 班级留言教师发送 Request VO")
@Data
public class CourseManageTeacherMessageReqVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "班级 ID 不能为空")
    private Long classId;

    @Schema(description = "学生 ID（会话归属）", requiredMode = Schema.RequiredMode.REQUIRED, example = "144")
    @NotNull(message = "学生 ID 不能为空")
    private Long studentId;

    @Schema(description = "留言内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "留言内容不能为空")
    private String content;
}
