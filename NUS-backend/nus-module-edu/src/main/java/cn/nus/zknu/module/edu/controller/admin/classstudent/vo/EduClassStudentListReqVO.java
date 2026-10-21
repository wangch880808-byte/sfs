package cn.nus.zknu.module.edu.controller.admin.classstudent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 班级已加入学生列表 Request VO")
@Data
public class EduClassStudentListReqVO {

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "班级 ID 不能为空")
    private Long classId;

    @Schema(description = "搜索关键词（用户名/名称/手机号）", example = "zhangsan")
    private String keyword;

    @Schema(description = "部门 ID", example = "1")
    private Long deptId;

}
