package cn.nus.zknu.module.edu.controller.admin.term.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 学期信息 Response VO")
@Data
public class EduTermRespVO {

    @Schema(description = "学期 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "组织 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long organizationId;

    @Schema(description = "学期编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-2025-1")
    private String code;

    @Schema(description = "学期名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-2025学年上学期")
    private String name;

    @Schema(description = "开始日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-09-01")
    private LocalDate startsOn;

    @Schema(description = "结束日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-01-15")
    private LocalDate endsOn;

    @Schema(description = "学期状态（0正常 1已归档）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
