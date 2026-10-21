package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务提交记录 DO
 */
@TableName("edu_homework_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkRecordDO extends BaseDO {

    /**
     * 记录 ID
     */
    private Long id;
    /**
     * 关联任务 ID
     */
    private Long homeworkId;
    /**
     * 学生 ID
     */
    private Long studentId;
    /**
     * 提交内容
     */
    private String content;
    /**
     * 附件URL列表（JSON数组）
     */
    private String attachmentUrls;
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
    /**
     * 得分（0-100）
     */
    private Integer score;
    /**
     * 教师/AI评语
     */
    private String comment;
    /**
     * 批改方式：1=手动, 2=AI
     */
    private Integer correctType;
    /**
     * 批改时间
     */
    private LocalDateTime correctTime;
    /**
     * 状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交
     */
    private Integer status;
    /**
     * 累计打回重做次数
     */
    private Integer redoCount;
    /**
     * 打回重做的截止时间，为空时沿用任务截止时间
     */
    private LocalDateTime redoDeadline;

}
