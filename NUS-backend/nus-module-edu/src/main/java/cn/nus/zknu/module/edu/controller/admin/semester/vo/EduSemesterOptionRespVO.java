package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 可选学期 Response VO")
@Data
public class EduSemesterOptionRespVO {

    @Schema(description = "学期名称", example = "2026-2027 上学期")
    private String semesterName;

    @Schema(description = "学期开始日期", example = "2026-08-01")
    private LocalDate semesterStartDate;

    @Schema(description = "学期结束日期", example = "2027-01-31")
    private LocalDate semesterEndDate;

    @Schema(description = "是否当前学期", example = "true")
    private Boolean current;

    @Schema(description = "该学期的班级课程数（含已封存）", example = "12")
    private Integer classCourseCount;

    @Schema(description = "该学期在用的班级课程数", example = "10")
    private Integer activeClassCourseCount;
}
