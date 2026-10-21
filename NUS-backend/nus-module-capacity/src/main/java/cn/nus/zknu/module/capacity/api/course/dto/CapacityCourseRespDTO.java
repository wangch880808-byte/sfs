package cn.nus.zknu.module.capacity.api.course.dto;

import cn.nus.zknu.module.capacity.enums.CapacityCourseTypeConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实训课程 Response DTO（跨模块只读视图，不含课程团队；团队成员见 CapacityCourseApi）。
 */
@Data
public class CapacityCourseRespDTO {

    /** 公共课审批状态：审批通过 */
    public static final int PUBLIC_AUDIT_APPROVED = 2;

    private Long id;
    /** 一级指标 ID */
    private Long indicatorId;
    /** 一级指标名称（冗余） */
    private String indicatorName;
    @Schema(description = "课程类型（0=公共课，1=专属课）")
    private Integer isPublicCourse;
    /** 公共课审批状态：0 无需审批，1 待审批，2 审批通过，3 审批驳回 */
    private Integer publicAuditStatus;
    private String publicAuditRemark;
    private LocalDateTime publicAuditTime;
    private Long publicAuditUserId;
    private String courseName;
    private String courseImageUrl;
    private String courseIntroduction;
    /** 课程负责教师 ID */
    private Long teacherId;
    /** 状态：0 正常，1 已归档 */
    private Integer status;
    private LocalDateTime createTime;

    @Schema(description = "是否公共课，由课程类型派生（isPublicCourse=0 时为 true）", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Boolean getPublicCourse() {
        return Integer.valueOf(CapacityCourseTypeConstants.PUBLIC_COURSE).equals(isPublicCourse);
    }

    /** 在线公共课：公共课、审批通过、未归档。 */
    public boolean isOnlinePublicCourse() {
        return Integer.valueOf(CapacityCourseTypeConstants.PUBLIC_COURSE).equals(isPublicCourse)
                && Integer.valueOf(PUBLIC_AUDIT_APPROVED).equals(publicAuditStatus)
                && Integer.valueOf(0).equals(status);
    }

}
