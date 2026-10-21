package cn.nus.zknu.module.edu.dal.mysql.classcourse;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseAssistantDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface EduClassCourseAssistantMapper extends BaseMapperX<EduClassCourseAssistantDO> {

    default List<EduClassCourseAssistantDO> selectListByClassCourseId(Long classCourseId) {
        return selectList(new LambdaQueryWrapperX<EduClassCourseAssistantDO>()
                .eq(EduClassCourseAssistantDO::getClassCourseId, classCourseId)
                .orderByAsc(EduClassCourseAssistantDO::getId));
    }

    default List<EduClassCourseAssistantDO> selectListByClassCourseIds(Collection<Long> classCourseIds) {
        if (CollUtil.isEmpty(classCourseIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<EduClassCourseAssistantDO>()
                .in(EduClassCourseAssistantDO::getClassCourseId, classCourseIds)
                .orderByAsc(EduClassCourseAssistantDO::getId));
    }

    default boolean existsByClassCourseIdsAndTeacherId(Collection<Long> classCourseIds, Long teacherId) {
        if (CollUtil.isEmpty(classCourseIds) || teacherId == null) {
            return false;
        }
        return selectCount(new LambdaQueryWrapperX<EduClassCourseAssistantDO>()
                .in(EduClassCourseAssistantDO::getClassCourseId, classCourseIds)
                .eq(EduClassCourseAssistantDO::getTeacherId, teacherId)) > 0;
    }

}
