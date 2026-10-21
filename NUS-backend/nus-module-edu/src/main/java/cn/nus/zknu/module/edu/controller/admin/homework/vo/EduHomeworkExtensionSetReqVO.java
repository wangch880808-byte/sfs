package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 设置个别延期 Request VO")
@Data
public class EduHomeworkExtensionSetReqVO {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "请选择任务")
    private Long homeworkId;

    @Schema(description = "学生ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择 1 名学生")
    @Size(max = 500, message = "一次最多给 500 名学生延期")
    private List<Long> studentIds;

    @Schema(description = "延期后的截止时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择延期后的截止时间")
    private LocalDateTime deadline;

    @Schema(description = "延期原因", example = "病假")
    @Size(max = 200, message = "延期原因不超过 200 字")
    private String reason;

}
