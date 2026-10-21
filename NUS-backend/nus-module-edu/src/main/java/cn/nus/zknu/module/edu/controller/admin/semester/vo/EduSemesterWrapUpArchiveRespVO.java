package cn.nus.zknu.module.edu.controller.admin.semester.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 学期收尾封存结果 Response VO")
@Data
public class EduSemesterWrapUpArchiveRespVO {

    @Schema(description = "学期名称", example = "2025-2026 下学期")
    private String semesterName;

    @Schema(description = "已封存的班级课程数", example = "10")
    private Integer archivedClassCourseCount = 0;

    @Schema(description = "已封存的班级数", example = "1")
    private Integer archivedClassCount = 0;
}
