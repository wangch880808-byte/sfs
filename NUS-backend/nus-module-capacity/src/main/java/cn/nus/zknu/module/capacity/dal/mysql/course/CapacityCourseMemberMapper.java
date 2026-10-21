package cn.nus.zknu.module.capacity.dal.mysql.course;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface CapacityCourseMemberMapper extends BaseMapperX<CapacityCourseMemberDO> {

    default List<CapacityCourseMemberDO> selectListByCourseIdForUpdate(Long courseId) {
        return selectList(new LambdaQueryWrapperX<CapacityCourseMemberDO>()
                .eq(CapacityCourseMemberDO::getCourseId, courseId)
                .orderByAsc(CapacityCourseMemberDO::getId)
                .last("FOR UPDATE"));
    }

    default List<CapacityCourseMemberDO> selectListByCourseId(Long courseId) {
        return selectList(new LambdaQueryWrapperX<CapacityCourseMemberDO>()
                .eq(CapacityCourseMemberDO::getCourseId, courseId)
                .orderByAsc(CapacityCourseMemberDO::getId));
    }

    default List<CapacityCourseMemberDO> selectListByCourseIds(Collection<Long> courseIds) {
        if (CollUtil.isEmpty(courseIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<CapacityCourseMemberDO>()
                .in(CapacityCourseMemberDO::getCourseId, courseIds)
                .orderByAsc(CapacityCourseMemberDO::getId));
    }

    default boolean existsByCourseIdAndUserId(Long courseId, Long userId) {
        return selectCount(new LambdaQueryWrapperX<CapacityCourseMemberDO>()
                .eq(CapacityCourseMemberDO::getCourseId, courseId)
                .eq(CapacityCourseMemberDO::getUserId, userId)) > 0;
    }

}
