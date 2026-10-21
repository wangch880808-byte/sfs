package cn.nus.zknu.module.capacity.dal.dataobject.course;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实训课程 DO
 */
@TableName("capacity_course")
@Data
@EqualsAndHashCode(callSuper = true)
public class CapacityCourseDO extends BaseDO {

    private Long id;

    private Long indicatorId;

    private String indicatorName;

    /**
     * 是否公共实训课程（0 是 1 不是）
     */
    private Integer isPublicCourse;

    /**
     * 公共课审批状态（0 无需审批 1 待审批 2 审批通过 3 审批驳回）
     */
    private Integer publicAuditStatus;

    /**
     * 公共课审批意见
     */
    private String publicAuditRemark;

    /**
     * 公共课审批时间
     */
    private java.time.LocalDateTime publicAuditTime;

    /**
     * 公共课审批人
     */
    private Long publicAuditUserId;

    private String courseName;

    private String courseImageUrl;

    private String courseIntroduction;

    @TableField("teacher_Id")
    private Long teacherId;

    private String teachAssistantIds;

    /**
     * @see CommonStatusEnum
     */
    private Integer status;

}
