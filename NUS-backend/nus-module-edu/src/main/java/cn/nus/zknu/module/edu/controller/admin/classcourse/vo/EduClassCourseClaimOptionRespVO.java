package cn.nus.zknu.module.edu.controller.admin.classcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 公共课认领选项 Response VO")
@Data
public class EduClassCourseClaimOptionRespVO {
    private Long classId;
    private String className;
    private Integer gradeYear;
    private String deptName;
    private Long classCourseId;
    private Long claimTeacherId;
    private String claimTeacherName;
    @Schema(description = "AVAILABLE 可认领，MINE 本人已认领，TAKEN 他人已认领")
    private String state;
}
