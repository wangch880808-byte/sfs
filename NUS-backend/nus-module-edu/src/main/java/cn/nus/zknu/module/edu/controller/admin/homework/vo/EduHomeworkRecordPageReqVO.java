package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 任务记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkRecordPageReqVO extends PageParam {

    @Schema(description = "关联任务 ID", example = "1")
    private Long homeworkId;

    @Schema(description = "学生 ID", example = "1")
    private Long studentId;

    @Schema(description = "状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交", example = "1")
    private Integer status;

}
