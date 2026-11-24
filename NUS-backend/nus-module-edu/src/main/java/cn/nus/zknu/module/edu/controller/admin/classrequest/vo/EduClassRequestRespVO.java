package cn.nus.zknu.module.edu.controller.admin.classrequest.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 建班申请 Response VO")
@Data
public class EduClassRequestRespVO {

    @Schema(description = "申请 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long courseId;

    @Schema(description = "班级 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long classId;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String term;

    @Schema(description = "申请人 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long applicantId;

    @Schema(description = "审核状态：0 待审核，1 通过，2 拒绝", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "申请理由 / 拒绝原因", example = "新学期开班需要")
    private String reason;

    @Schema(description = "审核人 ID", example = "1024")
    private Long reviewerId;

    @Schema(description = "审核时间")
    private LocalDateTime reviewedAt;

    @Schema(description = "审核通过后创建的班课关联 ID", example = "1")
    private Long classCourseId;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "课程名称", example = "智慧教育导论")
    private String courseName;

    @Schema(description = "班级名称", example = "教育 2301 班")
    private String className;

    @Schema(description = "申请人名称", example = "张老师")
    private String applicantName;

    @Schema(description = "审核人名称", example = "李老师")
    private String reviewerName;

}
