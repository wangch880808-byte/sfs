package cn.nus.zknu.module.edu.controller.admin.questionbank.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "题库分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionBankPageReqVO extends PageParam {

    @Schema(description = "一级技能指标ID")
    private Long indicatorFirstId;

    @Schema(description = "二级技能指标ID")
    private Long indicatorSecondId;

    @Schema(description = "任务等级：0=专业,1=初级,2=中级,3=高级")
    private Integer homeworkLevel;

    @Schema(description = "标题搜索")
    private String title;

}
