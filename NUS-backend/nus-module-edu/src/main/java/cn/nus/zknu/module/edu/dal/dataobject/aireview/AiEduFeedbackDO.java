package cn.nus.zknu.module.edu.dal.dataobject.aireview;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI评分反馈 DO
 */
@TableName("ai_edu_feedback")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiEduFeedbackDO extends BaseDO {

    private Long id;
    /**
     * 教师ID
     */
    private Long teacherId;
    /**
     * 任务记录ID
     */
    private Long homeworkRecordId;
    /**
     * 反馈类型：strict/normal/loose
     */
    private String feedbackType;
    /**
     * 原始AI评分
     */
    private Integer originalScore;

}
