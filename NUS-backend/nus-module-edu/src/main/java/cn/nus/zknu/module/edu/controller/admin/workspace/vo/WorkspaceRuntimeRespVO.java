package cn.nus.zknu.module.edu.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工作台运行概况响应 VO（P1-3）
 */
@Schema(description = "工作台运行概况响应")
@Data
public class WorkspaceRuntimeRespVO {

    @Schema(description = "本周课堂数")
    private Long weekClassCount;
    @Schema(description = "本周提交数")
    private Long weekSubmissionCount;
    @Schema(description = "本周批改数")
    private Long weekGradingCount;
    @Schema(description = "本周 AI 调用数")
    private Long weekAiCallCount;
    @Schema(description = "每日趋势")
    private List<DailyStat> dailyStats;

    @Schema(description = "每日统计")
    @Data
    public static class DailyStat {
        @Schema(description = "日期")
        private String date;
        @Schema(description = "提交数")
        private Long submissions;
        @Schema(description = "批改数")
        private Long gradings;
    }
}
