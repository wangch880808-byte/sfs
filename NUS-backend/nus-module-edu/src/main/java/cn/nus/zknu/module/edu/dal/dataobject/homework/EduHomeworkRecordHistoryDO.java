package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 任务提交与评分轨迹。 */
@TableName("edu_homework_record_history")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkRecordHistoryDO extends BaseDO {
    private Long id;
    private Long recordId;
    private Long homeworkId;
    private Long studentId;
    private String eventType;
    private Integer fromStatus;
    private Integer toStatus;
    private Integer score;
    private String comment;
    private LocalDateTime redoDeadline;
    private Long aiReviewId;
    private Long operatorId;
}
