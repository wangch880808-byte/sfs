package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

@Schema(description = "管理后台 - 当前学期信息 Response VO")
@Data
public class EduSemesterRespVO {

    @JsonIgnore
    @Schema(hidden = true)
    private boolean configReadFailed;

    @Schema(description = "当前学期名称", example = "2025-2026-2")
    private String semesterName;

    @Schema(description = "学期开始日期", example = "2026-02-23")
    private LocalDate semesterStartDate;

    @Schema(description = "学期结束日期", example = "2026-07-05")
    private LocalDate semesterEndDate;

    @Schema(description = "是否自动更新学期", example = "true")
    private Boolean autoEnabled;
}
