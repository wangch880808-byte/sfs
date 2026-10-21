package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "学生端 - 课程详情 Response VO")
@Data
public class AppStudentCourseDetailRespVO {

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "课程名称", example = "教师口语训练")
    private String courseName;

    @Schema(description = "封面URL")
    private String coverUrl;

    @Schema(description = "课程描述")
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
    private List<Resource> resources;

    @Schema(description = "课程任务列表")
    private List<Homework> homeworkList;

    @Schema(description = "课程、班级或班课已封存，只能查看（往期课程）", example = "false")
    private Boolean archived;

    @Schema(description = "课程资源")
    @Data
    public static class Resource {
        @Schema(description = "资源ID", example = "1")
        private Long id;
        @Schema(description = "资源名称", example = "课件1.pdf")
        private String resourceName;
        @Schema(description = "文件类型")
        private String fileType;
        @Schema(description = "文件大小（字节）")
        private Long fileSize;
    }

    @Schema(description = "课程任务")
    @Data
    public static class Homework {
        private Long homeworkId;
        private String homeworkName;
        private Integer homeworkType;
        private Integer homeworkLevel;
        private java.time.LocalDateTime deadline;
        @Schema(description = "提交状态：0未提交、1待批改、2已批改、3退回、4重新提交")
        private Integer status;
        private Integer score;
    }

}
