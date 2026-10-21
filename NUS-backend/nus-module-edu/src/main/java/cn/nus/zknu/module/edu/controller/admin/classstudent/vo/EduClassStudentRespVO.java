package cn.nus.zknu.module.edu.controller.admin.classstudent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 班级学生 Response VO")
@Data
public class EduClassStudentRespVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "用户账号", example = "zhangsan")
    private String username;

    @Schema(description = "用户名称", example = "张三")
    private String nickname;

    @Schema(description = "手机号", example = "13800000000")
    private String mobile;

    @Schema(description = "部门 ID", example = "1")
    private Long deptId;

    @Schema(description = "所属年级（入学年份）", example = "2026")
    private Integer gradeYear;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "留级")
    private String remark;

}
