package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 任务截止前提醒记录，用于保证每名学生每个任务只自动提醒一次。 */
@TableName("edu_homework_remind_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkRemindLogDO extends BaseDO {

    /** 截止前自动提醒 */
    public static final String TYPE_AUTO = "AUTO";

    private Long id;
    private Long homeworkId;
    private Long studentId;
    private String remindType;
    /** 提醒时学生的实际截止时间 */
    private LocalDateTime deadline;

}
