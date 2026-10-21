package cn.nus.zknu.module.edu.controller.admin.coursemanage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 实训课程班级参与详情 Response VO")
@Data
public class CourseManageClassDetailRespVO {

    @Schema(description = "班课关联 ID", example = "10")
    private Long classCourseId;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "课程名称", example = "粉笔字训练")
    private String courseName;

    @Schema(description = "指标名称", example = "书写能力")
    private String indicatorName;

    @Schema(description = "授课教师姓名", example = "张老师")
    private String teacherName;

    @Schema(description = "当前用户能否操作这个班课")
    private Boolean canOperate;

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
    private String classStatus;

    @Schema(description = "班级人数", example = "45")
    private Integer studentCount;

    @Schema(description = "助教姓名列表", example = "李老师、王老师")
    private String assistantTeacherNames;

    @Schema(description = "任务列表")
    private List<ClassTaskRespVO> tasks;

    @Schema(description = "班级成员")
    private List<ClassMemberRespVO> members;

    @Schema(description = "成绩分析")
    private ScoreAnalysisRespVO scoreAnalysis;

    @Schema(description = "留言问答列表")
    private List<ClassMessageRespVO> messages;

    @Data
    public static class ClassTaskRespVO {
        @Schema(description = "任务 ID", example = "101")
        private Long homeworkId;
        @Schema(description = "任务名称", example = "任务2：课程管理页面实现")
        private String homeworkName;
        @Schema(description = "发布时间")
        private LocalDateTime publishTime;
        @Schema(description = "截止时间")
        private LocalDateTime deadline;
        @Schema(description = "提交人数", example = "25")
        private Integer submittedCount;
        @Schema(description = "已批改人数", example = "18")
        private Integer correctedCount;
        @Schema(description = "平均分", example = "84.1")
        private Double avgScore;
    }

    @Data
    public static class ClassMemberRespVO {
        @Schema(description = "学生 ID", example = "144")
        private Long studentId;
        @Schema(description = "学号", example = "202301001")
        private String studentNo;
        @Schema(description = "姓名", example = "张三")
        private String studentName;
        @Schema(description = "头像")
        private String avatar;
        @Schema(description = "任务完成数", example = "2")
        private Integer finishedTaskCount;
        @Schema(description = "任务总数", example = "3")
        private Integer totalTaskCount;
        @Schema(description = "平均分", example = "88.5")
        private Double avgScore;
    }

    @Data
    public static class ScoreAnalysisRespVO {
        @Schema(description = "班级均分", example = "82.4")
        private Double avgScore;
        @Schema(description = "最高分", example = "96")
        private Double highestScore;
        @Schema(description = "最低分", example = "61")
        private Double lowestScore;
        @Schema(description = "及格率（%）", example = "90.0")
        private Double passRate;
        @Schema(description = "分数段分布")
        private List<ScoreRangeRespVO> scoreRanges;
    }

    @Data
    public static class ScoreRangeRespVO {
        @Schema(description = "分数段", example = "80-89")
        private String range;
        @Schema(description = "人数", example = "12")
        private Integer count;
        @Schema(description = "占比（%）", example = "26.7")
        private Double percent;
    }

    @Data
    public static class ClassMessageRespVO {
        @Schema(description = "消息 ID", example = "1001")
        private Long id;
        @Schema(description = "学生 ID（会话归属）", example = "144")
        private Long studentId;
        @Schema(description = "发送人 ID", example = "1")
        private Long senderId;
        @Schema(description = "发送人名称", example = "张三")
        private String senderName;
        @Schema(description = "发送人头像")
        private String senderAvatar;
        @Schema(description = "消息类型：0=学生留言,1=教师回复", example = "0")
        private Integer type;
        @Schema(description = "内容")
        private String content;
        @Schema(description = "发送时间")
        private LocalDateTime createTime;
    }
}
