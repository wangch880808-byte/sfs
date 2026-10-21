package cn.nus.zknu.module.edu.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "总体合格率响应")
@Data
public class TotalPassRateRespVO {

    @Schema(description = "涉及学生总人数")
    private Integer totalCount;

    @Schema(description = "不合格率")
    private Double unqualifiedRate;

    @Schema(description = "合格率")
    private Double qualifiedRate;

    @Schema(description = "良好率")
    private Double goodRate;

    @Schema(description = "优秀率")
    private Double excellentRate;

}
