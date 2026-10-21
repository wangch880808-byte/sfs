package cn.nus.zknu.module.capacity.controller.admin.course.vo;

import cn.nus.zknu.module.capacity.enums.CapacityCourseTypeConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 实训课程信息 Response VO")
@Data
public class CapacityCourseRespVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "指标 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long indicatorId;

    @Schema(description = "指标名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "毕业要求 1")
    private String indicatorName;

    @Schema(description = "课程类型（0=公共课，1=专属课）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer isPublicCourse;

    @Schema(description = "公共课审批状态（0无需审批 1待审批 2审批通过 3审批驳回）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer publicAuditStatus;

    @Schema(description = "公共课审批意见", example = "课程内容完整，可作为公共课开放")
    private String publicAuditRemark;

    @Schema(description = "公共课审批时间")
    private LocalDateTime publicAuditTime;

    @Schema(description = "公共课审批人 ID", example = "1")
    private Long publicAuditUserId;

    @Schema(description = "实训课程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Web 前端开发实训")
    private String courseName;

    @Schema(description = "课程封面地址", example = "https://domain/course.png")
    private String courseImageUrl;

    @Schema(description = "课程简介", example = "课程面向前端基础能力训练")
    private String courseIntroduction;

    @Schema(description = "所属教师 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long teacherId;

    @Schema(description = "助教教师团队 id，多个以英文逗号分隔", example = "2,3")
    private String teachAssistantIds;

    @Schema(description = "状态（0正常 1已封存）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "是否公共课，由课程类型派生（isPublicCourse=0 时为 true）", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Boolean getPublicCourse() {
        return Integer.valueOf(CapacityCourseTypeConstants.PUBLIC_COURSE).equals(isPublicCourse);
    }

}
