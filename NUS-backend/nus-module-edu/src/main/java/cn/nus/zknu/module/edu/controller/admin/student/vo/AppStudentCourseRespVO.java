package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "学生端 - 课程列表 Response VO")
@Data
public class AppStudentCourseRespVO {

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "课程名称", example = "教师口语训练")
    private String courseName;

    @Schema(description = "封面URL", example = "https://oss.example.com/cover.jpg")
    private String coverUrl;

    @Schema(description = "课程描述", example = "本课程旨在...")
    private String description;

    @Schema(description = "教师ID", example = "100")
    private Long teacherId;

    @Schema(description = "授课教师名称", example = "张老师")
    private String teacherName;

    @Schema(description = "课程一级指标ID", example = "6")
    private Long indicatorId;

    @Schema(description = "课程一级指标名称", example = "学科素养技能")
    private String indicatorName;

    @Schema(description = "总任务数", example = "10")
    private Integer totalHomework;

    @Schema(description = "已完成任务数", example = "5")
    private Integer completedHomework;

    @Schema(description = "平均分")
    private BigDecimal avgScore;

    @Schema(description = "课程资源列表")
    private List<AppStudentCourseDetailRespVO.Resource> resources;

    @Schema(description = "当前学生的课程任务列表")
    private List<AppStudentCourseDetailRespVO.Homework> homeworkList;

}
