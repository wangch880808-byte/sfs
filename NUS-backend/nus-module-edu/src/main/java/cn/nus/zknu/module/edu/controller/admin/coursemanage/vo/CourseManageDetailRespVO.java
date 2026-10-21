package cn.nus.zknu.module.edu.controller.admin.coursemanage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 实训课程详情（课程页） Response VO")
@Data
public class CourseManageDetailRespVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long courseId;

    @Schema(description = "当前用户能否管理课程", example = "true")
    private Boolean canManage;

    @Schema(description = "公共课按班级认领")
    private Boolean claimMode;

    @Schema(description = "当前用户能否认领公共课班级")
    private Boolean canClaim;

    @Schema(description = "可只读查看公共课全部班级和任务")
    private Boolean viewAllReadOnly;

    @Schema(description = "课程名称", example = "粉笔字训练")
    private String courseName;

    @Schema(description = "课程详情", example = "课程简介文本")
    private String courseIntroduction;

    @Schema(description = "课程封面图")
    private String courseImageUrl;

    @Schema(description = "实训类别", example = "公共实训")
    private String trainingCategory;

    @Schema(description = "指标名称", example = "书写能力")
    private String indicatorName;

    @Schema(description = "一级指标 ID", example = "1")
    private Long indicatorId;

    @Schema(description = "授课教师 ID", example = "1")
    private Long teacherId;

    @Schema(description = "授课教师姓名", example = "张老师")
    private String teacherName;

    @Schema(description = "课程下班级列表")
    private List<CourseClassRespVO> classes;

    @Schema(description = "课程下任务列表")
    private List<CourseTaskRespVO> tasks;

    @Schema(description = "课程下资源列表")
    private List<CourseResourceRespVO> resources;

    @Schema(description = "课程页班级信息")
    @Data
    public static class CourseClassRespVO {
        @Schema(description = "班课关联 ID", example = "10")
        private Long classCourseId;
        @Schema(description = "班课负责人 ID（公共课未认领时为空）")
        private Long ownerId;
        @Schema(description = "班课负责人姓名（公共课未认领时为未认领）")
        private String ownerName;
        @Schema(description = "认领教师 ID")
        private Long claimTeacherId;
        @Schema(description = "认领教师姓名")
        private String claimTeacherName;
        @Schema(description = "当前用户能否操作这个班课")
        private Boolean canOperate;
        @Schema(description = "当前用户能否放弃或清除该行认领")
        private Boolean canRelease;
        @Schema(description = "班级 ID", example = "1")
        private Long classId;
        @Schema(description = "班级名称", example = "软件工程 2023 级 1 班")
        private String className;
        @Schema(description = "学期名称", example = "2025-2026-2")
        private String semesterName;
        @Schema(description = "学期开始日期", example = "2026-02-23")
        private LocalDate semesterStartDate;
        @Schema(description = "学期结束日期", example = "2026-07-05")
        private LocalDate semesterEndDate;
        @Schema(description = "班级状态", example = "进行中")
        private String status;
        @Schema(description = "是否已封存（班课或班级已封存）", example = "false")
        private Boolean archived;
        @Schema(description = "班级人数", example = "45")
        private Integer studentCount;
        @Schema(description = "助教教师 ID 串", example = "101,102")
        private String assistantTeacherIds;
        @Schema(description = "助教姓名列表", example = "李老师、王老师")
        private String assistantTeacherNames;
        @Schema(description = "绑定时间")
        private LocalDateTime createTime;
    }

    @Schema(description = "课程页任务信息")
    @Data
    public static class CourseTaskRespVO {
        @Schema(description = "当前用户是否可修改、封存或恢复该任务")
        private Boolean canManage;
        @Schema(description = "当前用户是否可批改该任务")
        private Boolean canGrade;
        @Schema(description = "任务 ID", example = "100")
        private Long homeworkId;
        @Schema(description = "任务布置者 ID")
        private Long teacherId;
        @Schema(description = "任务布置者姓名，查不到时为 -")
        private String teacherName;
        @Schema(description = "任务状态：0=待发布，1=已发布，2=已截止，3=已封存")
        private Integer status;
        @Schema(description = "任务名称", example = "任务1：前端脚手架搭建")
        private String homeworkName;
        @Schema(description = "班级 ID", example = "1")
        private Long classId;
        @Schema(description = "班级名称", example = "软件工程 2023 级 1 班")
        private String className;
        @Schema(description = "学期名称", example = "2025-2026-2")
        private String semesterName;
        @Schema(description = "截止时间")
        private LocalDateTime deadline;
        @Schema(description = "班级人数", example = "45")
        private Integer studentCount;
        @Schema(description = "提交人数", example = "42")
        private Integer submittedCount;
        @Schema(description = "已批改人数", example = "36")
        private Integer correctedCount;
        @Schema(description = "平均分", example = "84.5")
        private Double avgScore;
    }

    @Schema(description = "课程页资源信息")
    @Data
    public static class CourseResourceRespVO {
        @Schema(description = "资源 ID", example = "88")
        private Long id;
        @Schema(description = "资源名称", example = "项目答辩评分示例")
        private String resourceName;
        @Schema(description = "文件 URL")
        private String fileUrl;
        @Schema(description = "文件类型")
        private String fileType;
        @Schema(description = "文件大小（字节）")
        private Long fileSize;
        @Schema(description = "上传时间")
        private LocalDateTime createTime;
    }
}
