package cn.nus.zknu.module.edu.controller.admin.student.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "学生端 - 任务列表 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppStudentHomeworkPageReqVO extends PageParam {

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交", example = "1")
    private Integer status;

    /** 往期任务：班级、课程或班课已归档，或任务已归档但提交过 */
    public static final String SCOPE_HISTORY = "history";

    @Schema(description = "范围：current=当前任务（默认），history=往期任务（只读）", example = "current")
    private String scope;

}
