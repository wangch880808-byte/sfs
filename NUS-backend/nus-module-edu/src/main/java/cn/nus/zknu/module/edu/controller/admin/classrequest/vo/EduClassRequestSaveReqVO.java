package cn.nus.zknu.module.edu.controller.admin.classrequest.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 建班申请提交 Request VO")
@Data
public class EduClassRequestSaveReqVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "班级 ID 不能为空")
    private Long classId;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String term;

    @Schema(description = "申请理由", example = "新学期开班需要")
    private String reason;

}
