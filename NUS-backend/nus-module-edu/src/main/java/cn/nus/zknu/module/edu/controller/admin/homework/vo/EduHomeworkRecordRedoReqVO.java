package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 任务打回重做 Request VO")
@Data
public class EduHomeworkRecordRedoReqVO {

    @Schema(description = "记录 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "记录 ID 不能为空")
    private Long id;

    @Schema(description = "重做截止时间；任务已截止时必填，且必须晚于当前时间", example = "2026-10-08 23:59:59")
    private LocalDateTime redoDeadline;

    @Schema(description = "读取这份提交时的更新时间；传入后若记录已被他人更新则拒绝保存", example = "1759800000000")
    private LocalDateTime expectedUpdateTime;

}
