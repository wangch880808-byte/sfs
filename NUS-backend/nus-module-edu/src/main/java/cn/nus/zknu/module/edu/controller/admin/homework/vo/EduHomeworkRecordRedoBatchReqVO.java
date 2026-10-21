package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 任务批量打回重做 Request VO")
@Data
public class EduHomeworkRecordRedoBatchReqVO {

    @Schema(description = "记录 ID 列表，单次最多 200 条", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1024, 1025]")
    @NotEmpty(message = "记录 ID 列表不能为空")
    @Size(max = 200, message = "单次最多打回 200 条记录")
    private List<Long> ids;

    @Schema(description = "重做截止时间；任务已截止时必填，且必须晚于当前时间", example = "2026-10-08 23:59:59")
    private LocalDateTime redoDeadline;

}
