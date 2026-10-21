package cn.nus.zknu.module.edu.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "课程分析响应")
@Data
public class CourseAnalysisRespVO {

    @Schema(description = "任务分析列表")
    private List<HomeworkAnalysis> homeworks;

    @Data
    public static class HomeworkAnalysis {
        @Schema(description = "任务ID")
        private Long homeworkId;
        @Schema(description = "任务名称")
        private String homeworkName;
        @Schema(description = "平均分")
        private Double avgScore;
        @Schema(description = "前5名学生")
        private List<StudentScore> top5;
        @Schema(description = "后5名学生")
        private List<StudentScore> last5;
    }

    @Data
    public static class StudentScore {
        @Schema(description = "学生ID")
        private Long studentId;
        @Schema(description = "学生姓名")
        private String studentName;
        @Schema(description = "分数")
        private Integer score;
    }

}
