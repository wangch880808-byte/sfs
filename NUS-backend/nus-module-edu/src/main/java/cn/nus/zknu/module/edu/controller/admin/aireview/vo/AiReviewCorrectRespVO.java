package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "AI批改单份响应")
@Data
public class AiReviewCorrectRespVO {

    @Schema(description = "AI评分")
    private Integer score;

    @Schema(description = "AI评语")
    private String comment;

    @Schema(description = "耗时(毫秒)")
    private Integer costTime;

}
