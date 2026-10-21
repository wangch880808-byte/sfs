package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 当前学期信息保存 Request VO")
@Data
public class EduSemesterSaveReqVO {

    @Schema(description = "当前学期名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-2026-2")
    @NotBlank(message = "学期名称不能为空")
    private String semesterName;

    @Schema(description = "学期开始日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-02-23")
    @NotNull(message = "学期开始日期不能为空")
    private LocalDate semesterStartDate;

    @Schema(description = "学期结束日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-07-05")
    @NotNull(message = "学期结束日期不能为空")
    private LocalDate semesterEndDate;
}

