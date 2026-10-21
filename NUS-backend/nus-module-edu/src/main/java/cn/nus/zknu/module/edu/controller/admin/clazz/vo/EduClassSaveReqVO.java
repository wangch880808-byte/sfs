package cn.nus.zknu.module.edu.controller.admin.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 班级新增/修改 Request VO")
@Data
public class EduClassSaveReqVO {

    @Schema(description = "班级 ID", example = "1024")
    private Long id;

    @Schema(description = "班级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024 级计算机科学与技术 1 班")
    @NotBlank(message = "班级名称不能为空")
    private String className;

    @Schema(description = "所属年级（入学年份）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024")
    @NotNull(message = "所属年级不能为空")
    private Integer gradeYear;

    @Schema(description = "部门 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "归属组织不能为空")
    private Long deptId;

    @Schema(description = "教育层次（1-专科/2-本科/3-硕士/4-博士）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "教育层次不能为空")
    private Long educationLevel;

    @Schema(description = "学制（2/3/4/5 年）", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "学制不能为空")
    private Long schoolingYear;

    @Schema(description = "班级状态（0正常 1已封存）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "班级状态不能为空")
    private Integer status;

    @Schema(description = "备注", example = "24 级本科班")
    private String remark;

}
