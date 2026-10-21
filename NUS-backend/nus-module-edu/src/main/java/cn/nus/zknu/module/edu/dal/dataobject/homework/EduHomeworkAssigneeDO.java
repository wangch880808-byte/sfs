package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务发布时的学生快照。
 */
@TableName("edu_homework_assignee")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkAssigneeDO extends BaseDO {

    private Long id;
    private Long homeworkId;
    private Long studentId;
    private Long classId;
    private LocalDateTime assignedTime;

}
