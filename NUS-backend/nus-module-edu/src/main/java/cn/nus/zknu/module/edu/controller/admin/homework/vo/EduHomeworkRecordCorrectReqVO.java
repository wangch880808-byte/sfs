package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 任务批改 Request VO")
@Data
public class EduHomeworkRecordCorrectReqVO {

    @Schema(description = "记录 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "记录 ID 不能为空")
    private Long id;

    @Schema(description = "得分（0-100）", requiredMode = Schema.RequiredMode.REQUIRED, example = "85")
    @NotNull(message = "得分不能为空")
    @Min(value = 0, message = "得分最小为 0")
    @Max(value = 100, message = "得分最大为 100")
    private Integer score;

    @Schema(description = "评语", example = "完成度较好，注意代码规范")
    private String comment;

    @Schema(description = "读取这份提交时的更新时间；传入后若记录已被他人更新则拒绝保存", example = "1759800000000")
    private LocalDateTime expectedUpdateTime;

}
