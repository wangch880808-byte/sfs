package cn.nus.zknu.module.capacity.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 实训课程新增/修改 Request VO")
@Data
public class CapacityCourseSaveReqVO {

    @Schema(description = "课程 ID", example = "1024")
    private Long id;

    @Schema(description = "指标 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "关联指标不能为空")
    private Long indicatorId;

    @Schema(description = "课程类型（0=公共课，1=专属课）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "课程类型不能为空")
    private Integer isPublicCourse;

    @Schema(description = "实训课程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Web 前端开发实训")
    @NotBlank(message = "实训课程名称不能为空")
    private String courseName;

    @Schema(description = "课程封面地址", example = "https://domain/course.png")
    private String courseImageUrl;

    @Schema(description = "课程简介", example = "课程面向前端基础能力训练")
    @Size(max = 1000, message = "课程简介长度不能超过 1000 个字符")
    private String courseIntroduction;

    @Schema(description = "所属教师 ID，为空时默认当前登录人", example = "1")
    private Long teacherId;

    @Schema(description = "助教教师团队 id，多个以英文逗号分隔", example = "2,3")
    private String teachAssistantIds;

    @Schema(description = "状态（0正常 1已封存）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

}
