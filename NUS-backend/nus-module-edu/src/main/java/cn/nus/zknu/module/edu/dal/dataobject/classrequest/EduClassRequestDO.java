package cn.nus.zknu.module.edu.dal.dataobject.classrequest;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 建班申请 DO
 */
@TableName("edu_class_request")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassRequestDO extends BaseDO {

    /**
     * 申请 ID
     */
    private Long id;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 班级 ID
     */
    private Long classId;
    /**
     * 学期名称（示例：2025-2026-2）
     */
    private String term;
    /**
     * 申请人（用户 ID）
     */
    private Long applicantId;
    /**
     * 审核状态：0 待审核，1 通过，2 拒绝
     */
    private Integer status;
    /**
     * 申请理由 / 拒绝原因
     */
    private String reason;
    /**
     * 审核人（用户 ID）
     */
    private Long reviewerId;
    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;
    /**
     * 审核通过后创建的班课关联 ID
     */
    private Long classCourseId;

}
