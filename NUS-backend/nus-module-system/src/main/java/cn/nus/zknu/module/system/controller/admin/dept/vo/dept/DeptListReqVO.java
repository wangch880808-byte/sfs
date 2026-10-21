package cn.nus.zknu.module.system.controller.admin.dept.vo.dept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 部门列表 Request VO")
@Data
public class DeptListReqVO {

    @Schema(description = "组织编码，模糊匹配", example = "COLLEGE")
    private String deptCode;

    @Schema(description = "组织类型：SCHOOL/COLLEGE/MAJOR", example = "COLLEGE")
    private String deptType;

    @Schema(description = "部门名称，模糊匹配", example = "教师教育学院")
    private String name;

    @Schema(description = "展示状态，参见 CommonStatusEnum 枚举类", example = "1")
    private Integer status;

}
