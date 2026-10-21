package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 学期收尾预览 Response VO")
@Data
public class EduSemesterWrapUpPreviewRespVO {

    @Schema(description = "学期名称", example = "2025-2026 下学期")
    private String semesterName;

    @Schema(description = "是否当前学期", example = "false")
    private Boolean current;

    @Schema(description = "该学期仍在用的班级课程数", example = "12")
    private Integer activeClassCourseCount = 0;

    @Schema(description = "仍在进行的任务数：已发布，且没有截止时间或尚未截止", example = "3")
    private Integer openTaskCount = 0;

    @Schema(description = "等待教师批改的提交数", example = "5")
    private Integer pendingReviewCount = 0;

    @Schema(description = "公共课同步创建的关联数（含已封存）", example = "40")
    private Integer publicCourseLinkCount = 0;

    @Schema(description = "公共课同步创建、仍在用的关联数", example = "8")
    private Integer activePublicCourseLinkCount = 0;

    @Schema(description = "仍在用的班级课程")
    private List<ClassCourse> classCourses = new ArrayList<>();

    @Schema(description = "只在该学期有课程安排、可以一起封存的班级")
    private List<ClassItem> classes = new ArrayList<>();

    @Data
    public static class ClassCourse {

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

        @Schema(description = "是否由公共课同步自动创建", example = "false")
        private Boolean autoCreated;

        @Schema(description = "仍在进行的任务数", example = "1")
        private Integer openTaskCount = 0;

        @Schema(description = "等待批改的提交数", example = "2")
        private Integer pendingReviewCount = 0;
    }

    @Data
    public static class ClassItem {

        @Schema(description = "班级 ID", example = "1")
        private Long classId;

        @Schema(description = "班级名称", example = "软件工程 1 班")
        private String className;

        @Schema(description = "该学期在用的班级课程数", example = "3")
        private Integer activeClassCourseCount = 0;
    }
}
