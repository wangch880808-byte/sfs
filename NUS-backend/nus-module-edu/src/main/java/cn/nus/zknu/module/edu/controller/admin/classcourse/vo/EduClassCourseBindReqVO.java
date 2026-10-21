package cn.nus.zknu.module.edu.controller.admin.classcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 班级绑定课程 Request VO")
@Data
public class EduClassCourseBindReqVO {

    @Schema(description = "课程 ID（课程维度绑定时必填）", example = "1")
    private Long courseId;

    @Schema(description = "班级 ID 列表（课程维度绑定时必填）", example = "[1, 2, 3]")
    private List<Long> classIds;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String semesterName;

    @Schema(description = "学期开始日期", example = "2026-02-23")
    private LocalDate semesterStartDate;

    @Schema(description = "学期结束日期", example = "2026-07-05")
    private LocalDate semesterEndDate;

    @Schema(description = "助教教师 ID 串，逗号分隔", example = "101,102")
    private String assistantTeacherIds;

    @Schema(description = "兼容旧接口：班级 ID（班级维度绑定时使用）", example = "1")
    private Long classId;

    @Schema(description = "兼容旧接口：课程 ID 列表（班级维度绑定时使用）", example = "[1,2,3]")
    private List<Long> courseIds;

}
