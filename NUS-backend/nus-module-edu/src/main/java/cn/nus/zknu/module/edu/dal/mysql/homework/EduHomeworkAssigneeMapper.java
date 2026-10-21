package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface EduHomeworkAssigneeMapper extends BaseMapperX<EduHomeworkAssigneeDO> {

    default List<EduHomeworkAssigneeDO> selectListByHomeworkId(Long homeworkId) {
        return selectList(EduHomeworkAssigneeDO::getHomeworkId, homeworkId);
    }

    default List<EduHomeworkAssigneeDO> selectListByStudentId(Long studentId) {
        return selectList(EduHomeworkAssigneeDO::getStudentId, studentId);
    }

    default List<EduHomeworkAssigneeDO> selectListByHomeworkIds(Collection<Long> homeworkIds) {
        if (CollUtil.isEmpty(homeworkIds)) {
            return Collections.emptyList();
        }
        return selectList(EduHomeworkAssigneeDO::getHomeworkId, homeworkIds);
    }

    default EduHomeworkAssigneeDO selectByHomeworkIdAndStudentId(Long homeworkId, Long studentId) {
        return selectOne(new LambdaQueryWrapperX<EduHomeworkAssigneeDO>()
                .eq(EduHomeworkAssigneeDO::getHomeworkId, homeworkId)
                .eq(EduHomeworkAssigneeDO::getStudentId, studentId));
    }

    default void deleteByHomeworkId(Long homeworkId) {
        delete(EduHomeworkAssigneeDO::getHomeworkId, homeworkId);
    }

    @Update("UPDATE edu_homework_assignee SET deleted = 0, assigned_time = NOW(), update_time = NOW() "
            + "WHERE homework_id = #{homeworkId} AND student_id = #{studentId} AND deleted = 1")
    int restore(@Param("homeworkId") Long homeworkId, @Param("studentId") Long studentId);

    @Update("UPDATE edu_homework_assignee SET deleted = 1, update_time = NOW() "
            + "WHERE homework_id = #{homeworkId} AND student_id = #{studentId} AND deleted = 0")
    int remove(@Param("homeworkId") Long homeworkId, @Param("studentId") Long studentId);

}
