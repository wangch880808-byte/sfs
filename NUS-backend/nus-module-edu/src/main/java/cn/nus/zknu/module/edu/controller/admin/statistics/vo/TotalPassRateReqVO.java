package cn.nus.zknu.module.edu.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "总体合格率请求")
@Data
public class TotalPassRateReqVO {

    @Schema(description = "学院ID")
    private Long collegeId;

    @Schema(description = "专业ID")
    private Long majorId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "课程ID")
    private Long courseId;

    @Schema(description = "学年起始年份，如2025表示2025-2026学年")
    private Integer academicYear;

}
