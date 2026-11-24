package cn.nus.zknu.module.edu.controller.admin.officialcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 正式课程新增/修改 Request VO")
@Data
public class EduOfficialCourseSaveReqVO {

    @Schema(description = "课程 ID", example = "1024")
    private Long id;

    @Schema(description = "部门 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "归属组织不能为空")
    private Long deptId;

    @Schema(description = "课程编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CS101")
    @NotBlank(message = "课程编码不能为空")
    private String code;

    @Schema(description = "课程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "数据结构")
    @NotBlank(message = "课程名称不能为空")
    private String name;

    @Schema(description = "学分", example = "3.0")
    private BigDecimal credit;

    @Schema(description = "课程状态（0正常 1已归档）", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "计算机专业核心课程")
    private String remark;

}
