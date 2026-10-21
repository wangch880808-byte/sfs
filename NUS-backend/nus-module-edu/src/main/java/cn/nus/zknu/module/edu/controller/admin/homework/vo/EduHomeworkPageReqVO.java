package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 实训任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkPageReqVO extends PageParam {

    @Schema(description = "任务名称，模糊匹配", example = "实训")
    private String homeworkName;

    @Schema(description = "任务类型：1=平时任务, 2=考核任务", example = "1")
    private Integer homeworkType;

    @Schema(description = "所属课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "所属班级 ID", example = "1")
    private Long classId;

    @Schema(description = "学期名称", example = "2025-2026-2")
    private String semesterName;

    @Schema(description = "状态：0=草稿, 1=已发布, 2=已截止, 3=已封存", example = "1")
    private Integer status;

    @Schema(description = "是否包含已封存任务，默认否")
    private Boolean includeArchived = false;

    @Schema(description = "创建时间", example = "[2026-03-01 00:00:00, 2026-03-07 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
