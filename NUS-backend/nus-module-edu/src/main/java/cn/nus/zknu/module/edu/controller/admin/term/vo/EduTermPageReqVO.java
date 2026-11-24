package cn.nus.zknu.module.edu.controller.admin.term.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 学期分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduTermPageReqVO extends PageParam {

    @Schema(description = "学期名称，模糊匹配", example = "2024")
    private String name;

    @Schema(description = "学期编码", example = "2024-2025-1")
    private String code;

    @Schema(description = "组织 ID", example = "1")
    private Long organizationId;

    @Schema(description = "学期状态（0正常 1已归档）", example = "0")
    private Integer status;

}
