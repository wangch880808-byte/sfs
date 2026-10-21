package cn.nus.zknu.module.capacity.dal.dataobject.course;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程团队成员 DO：一行一位协作教师，取代 capacity_course.teach_assistant_ids 逗号串。
 * <p>
 * 课程负责教师仍记在 capacity_course.teacher_Id，不在本表。
 * 同一课程同一用户只允许一条未删除的记录；移除成员为逻辑删除。
 */
@TableName("capacity_course_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class CapacityCourseMemberDO extends BaseDO {

    /** 成员角色：课程团队（协作教师）。 */
    public static final int ROLE_TEAM = 1;

    private Long id;

    /** 课程 ID */
    private Long courseId;

    /** 成员用户 ID */
    private Long userId;

    /** 成员角色，见 {@link #ROLE_TEAM} */
    private Integer roleType;

}
