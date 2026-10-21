package cn.nus.zknu.module.edu.controller.admin.classstudent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - 班级学生批量操作 Request VO")
@Data
public class EduClassStudentBatchReqVO {

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "班级 ID 不能为空")
    private Long classId;

    @Schema(description = "学生 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    @NotEmpty(message = "学生 ID 列表不能为空")
    private Set<Long> studentIds;

    @Schema(description = "备注", example = "留级")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
