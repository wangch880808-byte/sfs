package cn.nus.zknu.module.edu.controller.admin.officialcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 正式课程信息 Response VO")
@Data
public class EduOfficialCourseRespVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "部门 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long deptId;

    @Schema(description = "课程编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CS101")
    private String code;

    @Schema(description = "课程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "数据结构")
    private String name;

    @Schema(description = "学分", example = "3.0")
    private BigDecimal credit;

    @Schema(description = "课程状态（0正常 1已归档）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注", example = "计算机专业核心课程")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
