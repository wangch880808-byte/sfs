package cn.nus.zknu.module.system.dal.mysql.user;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.system.dal.dataobject.user.EduClassStudentRelDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface EduClassStudentRelMapper extends BaseMapperX<EduClassStudentRelDO> {

    default List<EduClassStudentRelDO> selectListByStudentId(Long studentId) {
        return selectList(EduClassStudentRelDO::getStudentId, studentId);
    }

    default void deleteByStudentId(Long studentId) {
        if (studentId == null) {
            return;
        }
        delete(EduClassStudentRelDO::getStudentId, studentId);
    }

    default void deleteByStudentIds(Collection<Long> studentIds) {
        if (CollUtil.isEmpty(studentIds)) {
            return;
        }
        deleteBatch(EduClassStudentRelDO::getStudentId, studentIds);
    }

    default void deleteByStudentIdAndClassIds(Long studentId, Collection<Long> classIds) {
        if (studentId == null || CollUtil.isEmpty(classIds)) {
            return;
        }
        delete(new LambdaQueryWrapperX<EduClassStudentRelDO>()
                .eq(EduClassStudentRelDO::getStudentId, studentId)
                .in(EduClassStudentRelDO::getClassId, classIds));
    }

    default List<EduClassStudentRelDO> selectListByStudentIdAndClassIds(Long studentId, Collection<Long> classIds) {
        if (studentId == null || CollUtil.isEmpty(classIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<EduClassStudentRelDO>()
                .eq(EduClassStudentRelDO::getStudentId, studentId)
                .in(EduClassStudentRelDO::getClassId, classIds));
    }

}
