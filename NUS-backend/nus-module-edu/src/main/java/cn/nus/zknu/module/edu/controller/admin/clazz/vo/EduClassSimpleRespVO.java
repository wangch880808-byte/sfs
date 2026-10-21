package cn.nus.zknu.module.edu.controller.admin.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 班级精简信息 Response VO")
@Data
public class EduClassSimpleRespVO {

    @Schema(description = "班级编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "班级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026级软件工程本科1班")
    private String className;

}
