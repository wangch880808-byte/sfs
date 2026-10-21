package cn.nus.zknu.module.edu.controller.admin.courseresource.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 课程资源分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduCourseResourcePageReqVO extends PageParam {

    @Schema(description = "所属课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long courseId;

    @Schema(description = "资源名称，模糊匹配", example = "课件")
    private String resourceName;

    @Schema(description = "MIME类型", example = "application/pdf")
    private String fileType;

}
