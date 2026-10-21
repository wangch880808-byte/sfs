package cn.nus.zknu.module.edu.dal.mysql.student;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.student.EduStudentLevelUnlockDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EduStudentLevelUnlockMapper extends BaseMapperX<EduStudentLevelUnlockDO> {

    default List<EduStudentLevelUnlockDO> selectListByStudentAndCourse(Long studentId, Long courseId) {
        return selectList(new LambdaQueryWrapperX<EduStudentLevelUnlockDO>()
                .eq(EduStudentLevelUnlockDO::getStudentId, studentId)
                .eq(EduStudentLevelUnlockDO::getCourseId, courseId));
    }

    /**
     * 只在第一次解锁时写入；并发重复写入由唯一键吸收，已有快照保持原样。
     *
     * @return 新写入的行数（0 表示已有快照）
     */
    @Insert("INSERT INTO edu_student_level_unlock (student_id, course_id, level, unlocked_at, ratio_at_unlock, "
            + "creator, create_time, updater, update_time, deleted) "
            + "VALUES (#{studentId}, #{courseId}, #{level}, #{unlockedAt}, #{ratioAtUnlock}, "
            + "#{operator}, NOW(), #{operator}, NOW(), b'0') "
            + "ON DUPLICATE KEY UPDATE id = id")
    int insertIfAbsent(@Param("studentId") Long studentId, @Param("courseId") Long courseId,
                       @Param("level") Integer level, @Param("unlockedAt") LocalDateTime unlockedAt,
                       @Param("ratioAtUnlock") BigDecimal ratioAtUnlock, @Param("operator") String operator);

}
