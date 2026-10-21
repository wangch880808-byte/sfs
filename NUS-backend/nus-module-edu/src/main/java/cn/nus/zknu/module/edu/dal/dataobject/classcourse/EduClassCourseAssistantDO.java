package cn.nus.zknu.module.edu.dal.dataobject.classcourse;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 班课助教 DO：一行一位助教，取代 edu_class_course.assistant_teacher_ids 逗号串。
 * <p>
 * 按班课关联（edu_class_course.id）挂靠，同一关联同一教师只允许一条未删除的记录；移除为逻辑删除。
 */
@TableName("edu_class_course_assistant")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassCourseAssistantDO extends BaseDO {

    private Long id;

    /** 班课关联 ID（edu_class_course.id） */
    private Long classCourseId;

    /** 助教教师用户 ID */
    private Long teacherId;

}
