package cn.nus.zknu.module.edu.dal.dataobject.aireview;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI批改异步任务 DO
 */
@TableName("ai_edu_review_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiEduReviewTaskDO extends BaseDO {

    private Long id;
    /**
     * 任务ID
     */
    private Long homeworkId;
    /**
     * 班级ID
     */
    private Long classId;
    /** 发起任务的教师，供后台任务与重启恢复使用。 */
    private Long teacherId;
    /** 非空表示已被一个实例认领。 */
    private String generationToken;
    private String errorMessage;
    /**
     * 总数
     */
    private Integer totalCount;
    /**
     * 成功数
     */
    private Integer successCount;
    /**
     * 失败数
     */
    private Integer failCount;
    /**
     * 0=处理中,1=完成,2=部分失败
     */
    private Integer status;

}
