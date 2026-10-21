package cn.nus.zknu.module.edu.controller.admin.clazz.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 班级分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassPageReqVO extends PageParam {

    @Schema(description = "班级名称，模糊匹配", example = "计算机")
    private String className;

    @Schema(description = "所属年级（入学年份）", example = "2024")
    private Integer gradeYear;

    @Schema(description = "部门 ID", example = "1")
    private Long deptId;

    @Schema(description = "教育层次（1-专科/2-本科/3-硕士/4-博士）", example = "2")
    private Long educationLevel;

    @Schema(description = "学制（2/3/4/5 年）", example = "4")
    private Long schoolingYear;

    @Schema(description = "班级状态（0正常 1已封存）", example = "0")
    private Integer status;

    @Schema(description = "是否包含已封存班级，默认否")
    private Boolean includeArchived = false;

    @Schema(description = "创建时间", example = "[2026-03-01 00:00:00, 2026-03-07 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
