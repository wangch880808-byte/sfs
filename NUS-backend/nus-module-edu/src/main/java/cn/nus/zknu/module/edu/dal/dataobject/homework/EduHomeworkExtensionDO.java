package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务个别延期：学生的实际截止时间取任务截止时间与个人截止时间中较晚的一个。
 * 同一任务同一学生只保留一条未删除的记录。
 */
@TableName("edu_homework_extension")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkExtensionDO extends BaseDO {

    private Long id;
    private Long homeworkId;
    private Long studentId;
    /** 个人截止时间 */
    private LocalDateTime deadline;
    /** 延期原因，可为空字符串 */
    private String reason;
    /** 设置延期的教师 */
    private Long operatorId;

}
