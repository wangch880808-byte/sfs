package cn.nus.zknu.module.edu.controller.admin.term.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.nus.zknu.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 学期新增/修改 Request VO")
@Data
public class EduTermSaveReqVO {

    @Schema(description = "学期 ID", example = "1024")
    private Long id;

    @Schema(description = "组织 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "组织不能为空")
    private Long organizationId;

    @Schema(description = "学期编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-2025-1")
    @NotBlank(message = "学期编码不能为空")
    private String code;

    @Schema(description = "学期名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-2025学年上学期")
    @NotBlank(message = "学期名称不能为空")
    private String name;

    @Schema(description = "开始日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-09-01")
    @NotNull(message = "开始日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate startsOn;

    @Schema(description = "结束日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-01-15")
    @NotNull(message = "结束日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate endsOn;

    @Schema(description = "学期状态（0正常 1已归档）", example = "0")
    private Integer status;

}
