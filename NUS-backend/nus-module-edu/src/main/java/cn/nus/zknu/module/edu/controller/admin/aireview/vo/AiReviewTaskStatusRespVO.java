package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "AI批改任务状态响应")
@Data
public class AiReviewTaskStatusRespVO {
    private String errorMessage;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "状态：0=处理中,1=完成,2=部分失败")
    private Integer status;

    @Schema(description = "总数")
    private Integer totalCount;

    @Schema(description = "成功数")
    private Integer successCount;

    @Schema(description = "失败数")
    private Integer failCount;

    @Schema(description = "进度百分比")
    private Integer progress;

}
