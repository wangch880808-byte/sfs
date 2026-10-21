package cn.nus.zknu.module.edu.dal.dataobject.student;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生等级解锁快照：规则第一次判定解锁时写入，之后不再收回。
 * <p>
 * 与解锁判定口径一致，按“学生 + 课程 + 等级”记录（任务分属不同班课，但等级在课程内统一计算）。
 */
@TableName("edu_student_level_unlock")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduStudentLevelUnlockDO extends BaseDO {

    private Long id;
    private Long studentId;
    private Long courseId;
    /** 解锁的等级：2=中级，3=高级（初级默认解锁，不记录） */
    private Integer level;
    private LocalDateTime unlockedAt;
    /** 解锁时上一等级的达标比例（0~1）；上一等级没有任务、直接放行时为空 */
    private BigDecimal ratioAtUnlock;

}
