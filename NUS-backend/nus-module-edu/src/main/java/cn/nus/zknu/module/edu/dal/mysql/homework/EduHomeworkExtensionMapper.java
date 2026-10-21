package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkExtensionDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface EduHomeworkExtensionMapper extends BaseMapperX<EduHomeworkExtensionDO> {

    default EduHomeworkExtensionDO selectByHomeworkIdAndStudentId(Long homeworkId, Long studentId) {
        return selectOne(new LambdaQueryWrapperX<EduHomeworkExtensionDO>()
                .eq(EduHomeworkExtensionDO::getHomeworkId, homeworkId)
                .eq(EduHomeworkExtensionDO::getStudentId, studentId));
    }

    default List<EduHomeworkExtensionDO> selectListByHomeworkId(Long homeworkId) {
        return selectList(new LambdaQueryWrapperX<EduHomeworkExtensionDO>()
                .eq(EduHomeworkExtensionDO::getHomeworkId, homeworkId)
                .orderByAsc(EduHomeworkExtensionDO::getDeadline)
                .orderByAsc(EduHomeworkExtensionDO::getId));
    }

    default List<EduHomeworkExtensionDO> selectListByHomeworkIds(Collection<Long> homeworkIds) {
        if (CollUtil.isEmpty(homeworkIds)) {
            return Collections.emptyList();
        }
        return selectList(EduHomeworkExtensionDO::getHomeworkId, homeworkIds);
    }

    default List<EduHomeworkExtensionDO> selectListByStudentIdAndHomeworkIds(Long studentId,
                                                                            Collection<Long> homeworkIds) {
        if (studentId == null || CollUtil.isEmpty(homeworkIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<EduHomeworkExtensionDO>()
                .eq(EduHomeworkExtensionDO::getStudentId, studentId)
                .in(EduHomeworkExtensionDO::getHomeworkId, homeworkIds));
    }

    /** 个人截止时间落在区间 (from, to] 内的延期，用于找出截止前需要提醒的任务。 */
    default List<EduHomeworkExtensionDO> selectListByDeadlineBetween(LocalDateTime from, LocalDateTime to) {
        return selectList(new LambdaQueryWrapperX<EduHomeworkExtensionDO>()
                .gt(EduHomeworkExtensionDO::getDeadline, from)
                .le(EduHomeworkExtensionDO::getDeadline, to));
    }

}
