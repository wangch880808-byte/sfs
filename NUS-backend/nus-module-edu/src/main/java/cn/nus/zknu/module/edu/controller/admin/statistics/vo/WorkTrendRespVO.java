package cn.nus.zknu.module.edu.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "任务成绩趋势响应")
@Data
public class WorkTrendRespVO {

    @Schema(description = "任务名称列表")
    private List<String> workNames;

    @Schema(description = "平均分列表")
    private List<Double> avgScores;

    @Schema(description = "最高分列表")
    private List<Integer> maxScores;

    @Schema(description = "最低分列表")
    private List<Integer> minScores;

}
