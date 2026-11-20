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
 * 按“学生 + 课程 + 一级指标（技能）+ 等级”记录：同一门课里不同一级技能各自独立闯关，互不影响。
 * indicatorFirstId 为 0 表示任务没填一级指标，这些任务仍按原来整门课的口径闯关。
 */
@TableName("edu_student_level_unlock")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduStudentLevelUnlockDO extends BaseDO {

    private Long id;
    private Long studentId;
    private Long courseId;
    /** 一级指标（技能）编号，0=未填指标（沿用整门课口径） */
    private Long indicatorFirstId;
    /** 解锁的等级：2=中级，3=高级（初级默认解锁，不记录） */
    private Integer level;
    private LocalDateTime unlockedAt;
    /** 解锁时上一等级的达标比例（0~1）；上一等级没有任务、直接放行时为空 */
    private BigDecimal ratioAtUnlock;

}
