package cn.nus.zknu.module.edu.controller.admin.homework.vo.copy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 任务可复制到的班级课程 Response VO")
@Data
public class EduHomeworkCopyTargetRespVO {

    @Schema(description = "班课关联 ID", example = "10")
    private Long classCourseId;

    @Schema(description = "班级 ID", example = "1")
    private Long classId;

    @Schema(description = "班级名称", example = "软件工程 1 班")
    private String className;

    @Schema(description = "课程 ID", example = "2")
    private Long courseId;

    @Schema(description = "课程名称", example = "Web 前端开发")
    private String courseName;

    @Schema(description = "学期名称", example = "2026-2027 上学期")
    private String semesterName;

    @Schema(description = "是否公共课", example = "false")
    private Boolean publicCourse;
}
