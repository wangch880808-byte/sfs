package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "学生端 - 实训报告基础数据 Response VO")
@Data
public class AppStudentReportBasicRespVO {

    @Schema(description = "全部历史可见任务数（含未关联一级指标的任务）", example = "30")
    private Integer totalTasks;

    @Schema(description = "已完成数", example = "20")
    private Integer submittedTasks;

    private Integer pendingReviewTasks;

    private Integer evaluatedTasks;

    @Schema(description = "达标数", example = "15")
    private Integer qualifiedTasks;

    @Schema(description = "全部任务中已达标数占已评定数的比例（百分比），含未关联一级指标的任务", example = "75.0")
    private Double qualifiedRate;

    @Schema(description = "按一级指标能力画像及证书阈值判定是否可获证；有已评定任务但无已评定指标时为 false，无已评定任务时为空")
    private Boolean certificateEligible;

    @Schema(description = "全部任务的评定状态，有已评定任务时为 EVALUATED")
    private String evaluationStatus;


}
