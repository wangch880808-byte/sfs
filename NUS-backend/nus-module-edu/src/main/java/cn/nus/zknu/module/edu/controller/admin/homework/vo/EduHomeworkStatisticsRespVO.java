package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 任务统计 Response VO")
@Data
public class EduHomeworkStatisticsRespVO {

    @Schema(description = "任务 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long homeworkId;

    @Schema(description = "总人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "50")
    private Integer totalCount;

    @Schema(description = "已提交人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "40")
    private Integer submittedCount;

    @Schema(description = "已批改人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    private Integer correctedCount;

    @Schema(description = "已打回人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer redoCount;

    @Schema(description = "达标人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "28")
    private Integer passCount;

    @Schema(description = "平均分", example = "78.5")
    private Double avgScore;

    @Schema(description = "最高分", example = "100")
    private Integer maxScore;

    @Schema(description = "最低分", example = "45")
    private Integer minScore;

    @Schema(description = "只读统计：任务已封存，或班级、课程、班课已封存", example = "false")
    private Boolean readOnly;

}
