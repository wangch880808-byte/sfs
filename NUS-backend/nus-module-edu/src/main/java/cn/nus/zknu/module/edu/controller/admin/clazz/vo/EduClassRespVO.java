package cn.nus.zknu.module.edu.controller.admin.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 班级信息 Response VO")
@Data
public class EduClassRespVO {

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "班级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024 级计算机科学与技术 1 班")
    private String className;

    @Schema(description = "所属年级（入学年份）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024")
    private Integer gradeYear;

    @Schema(description = "部门 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long deptId;

    @Schema(description = "教育层次（1-专科/2-本科/3-硕士/4-博士）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long educationLevel;

    @Schema(description = "学制（2/3/4/5 年）", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    private Long schoolingYear;

    @Schema(description = "班级状态（0正常 1已封存）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注", example = "24 级本科班")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
