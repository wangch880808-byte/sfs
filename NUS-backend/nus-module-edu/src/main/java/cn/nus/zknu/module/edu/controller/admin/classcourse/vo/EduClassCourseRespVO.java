package cn.nus.zknu.module.edu.controller.admin.classcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 班级课程关联 Response VO")
@Data
public class EduClassCourseRespVO {

    @Schema(description = "ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long classId;

    @Schema(description = "班级名称", example = "2025级汉语言文学本科1班")
    private String className;

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long courseId;

    @Schema(description = "课程名称", example = "测试课程1")
    private String courseName;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String semesterName;

    @Schema(description = "学期开始日期", example = "2026-02-23")
    private LocalDate semesterStartDate;

    @Schema(description = "学期结束日期", example = "2026-07-05")
    private LocalDate semesterEndDate;

    @Schema(description = "助教教师 ID 串，逗号分隔", example = "101,102")
    private String assistantTeacherIds;

    @Schema(description = "0正常 1已封存")
    private Integer status;

    @Schema(description = "认领教师 ID")
    private Long claimTeacherId;

    @Schema(description = "认领教师姓名")
    private String claimTeacherName;

    @Schema(description = "是否可操作该班课")
    private Boolean canOperate;

    @Schema(description = "当前用户能否放弃或清除该行认领")
    private Boolean canRelease;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
