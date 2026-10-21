package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "AI异步任务启动响应")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskStartRespVO {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long taskId;

}
