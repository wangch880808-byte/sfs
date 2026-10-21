package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 学期收尾封存 Request VO")
@Data
public class EduSemesterWrapUpArchiveReqVO {

    @Schema(description = "学期名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-2026 下学期")
    @NotBlank(message = "请选择要收尾的学期")
    private String semesterName;

    @Schema(description = "要封存的班课关联 ID", example = "[10, 11]")
    @Size(max = 2000, message = "一次最多封存 2000 个班级课程")
    private List<Long> classCourseIds = new ArrayList<>();

    @Schema(description = "要封存的班级 ID（只在该学期有课程安排的班级）", example = "[1]")
    @Size(max = 500, message = "一次最多封存 500 个班级")
    private List<Long> classIds = new ArrayList<>();
}
