package cn.nus.zknu.module.edu.controller.admin.classstudent.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 班级可加入学生分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassStudentPageReqVO extends PageParam {

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "班级 ID 不能为空")
    private Long classId;

    @Schema(description = "搜索关键词（用户名/名称/手机号）", example = "zhangsan")
    private String keyword;

    @Schema(description = "部门 ID", example = "1")
    private Long deptId;

    @Schema(description = "所属年级（入学年份）", example = "2026")
    private Integer gradeYear;

}
