package cn.nus.zknu.module.edu.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理员首页看板响应")
@Data
public class AdminDashboardRespVO {

    @Schema(description = "总学生数")
    private Long totalStudents;
    @Schema(description = "总教师数")
    private Long totalTeachers;
    @Schema(description = "总课程数")
    private Long totalCourses;
    @Schema(description = "总任务数")
    private Long totalHomeworks;
    @Schema(description = "本周提交数")
    private Long weekSubmissions;
    @Schema(description = "待批改数")
    private Long pendingCorrections;
    @Schema(description = "AI批改数")
    private Long aiCorrections;
    @Schema(description = "待审批公共课数量（含已封存课程）；无公共课审批权限时为空")
    private Integer pendingPublicAuditCount;
    @Schema(description = "学期总数")
    private Long termCount;
    @Schema(description = "正式课程目录数")
    private Long officialCourseCount;
    @Schema(description = "教学班（班课关联）数")
    private Long classCourseCount;
    @Schema(description = "活跃班级数（在用状态）")
    private Long activeClassCount;
    @Schema(description = "最近学期列表")
    private java.util.List<TermSummary> recentTerms;

    @Schema(description = "学期摘要")
    @Data
    public static class TermSummary {
        @Schema(description = "学期名称")
        private String name;
        @Schema(description = "开始日期")
        private String startsOn;
        @Schema(description = "结束日期")
        private String endsOn;
        @Schema(description = "状态 0 待启用 1 启用 2 归档")
        private Integer status;
    }

}
