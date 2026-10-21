package cn.nus.zknu.module.edu.controller.admin.questionbank.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "题库响应")
@Data
public class QuestionBankRespVO {

    @Schema(description = "ID")
    private Long id;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "任务要求")
    private String requirement;
    @Schema(description = "一级指标ID")
    private Long indicatorFirstId;
    @Schema(description = "一级指标名称")
    private String indicatorFirstName;
    @Schema(description = "二级指标ID")
    private Long indicatorSecondId;
    @Schema(description = "二级指标名称")
    private String indicatorSecondName;
    @Schema(description = "任务等级")
    private Integer homeworkLevel;
    @Schema(description = "教师ID")
    private Long teacherId;

}
