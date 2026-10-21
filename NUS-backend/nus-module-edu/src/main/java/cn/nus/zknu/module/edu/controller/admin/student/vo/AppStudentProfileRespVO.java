package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "学生端 - 个人信息 Response VO")
@Data
public class AppStudentProfileRespVO {

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "student001")
    private String username;

    @Schema(description = "名称", example = "张三")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "手机号", example = "13800138000")
    private String mobile;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "归属组织ID", example = "146")
    private Long deptId;

    @Schema(description = "归属组织名称", example = "汉语言文学")
    private String deptName;

    @Schema(description = "入学年份", example = "2025")
    private Integer gradeYear;

    @Schema(description = "班级ID列表", example = "[1,2]")
    private List<Long> classIds;

    @Schema(description = "班级名称列表")
    private List<String> classNames;

    @Schema(description = "班级名称", example = "2024级汉语言文学1班")
    private String className;

    @Schema(description = "专业名称", example = "汉语言文学")
    private String majorName;

}
