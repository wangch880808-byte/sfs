package cn.nus.zknu.module.capacity.controller.admin.course.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 实训课程分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CapacityCoursePageReqVO extends PageParam {

    @Schema(description = "课程名称，模糊匹配", example = "实训")
    private String courseName;

    @Schema(description = "指标 ID", example = "1")
    private Long indicatorId;

    @Schema(description = "课程类型（0=公共课，1=专属课）", example = "0")
    private Integer isPublicCourse;

    @Schema(description = "公共课审批状态（0无需审批 1待审批 2审批通过 3审批驳回）", example = "1")
    private Integer publicAuditStatus;

    @Schema(description = "所属教师 ID", example = "1")
    private Long teacherId;

    @Schema(description = "状态（0正常 1已封存）", example = "0")
    private Integer status;

    @Schema(description = "是否包含已封存课程，默认否")
    private Boolean includeArchived = false;

    @Schema(description = "创建时间", example = "[2026-03-01 00:00:00, 2026-03-07 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
