package cn.nus.zknu.module.edu.controller.admin.officialcourse.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 正式课程分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduOfficialCoursePageReqVO extends PageParam {

    @Schema(description = "课程名称，模糊匹配", example = "数据")
    private String name;

    @Schema(description = "课程编码", example = "CS101")
    private String code;

    @Schema(description = "部门 ID", example = "1")
    private Long deptId;

    @Schema(description = "课程状态（0正常 1已归档）", example = "0")
    private Integer status;

}
