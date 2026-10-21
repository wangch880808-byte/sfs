package cn.nus.zknu.module.capacity.dal.mysql.course;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCoursePageReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CapacityCourseMapper extends BaseMapperX<CapacityCourseDO> {

    default CapacityCourseDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<CapacityCourseDO>()
                .eq(CapacityCourseDO::getId, id).last("FOR UPDATE"));
    }

    default PageResult<CapacityCourseDO> selectPage(CapacityCoursePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CapacityCourseDO>()
                .likeIfPresent(CapacityCourseDO::getCourseName, reqVO.getCourseName())
                .eqIfPresent(CapacityCourseDO::getIndicatorId, reqVO.getIndicatorId())
                .eqIfPresent(CapacityCourseDO::getIsPublicCourse, reqVO.getIsPublicCourse())
                .eqIfPresent(CapacityCourseDO::getPublicAuditStatus, reqVO.getPublicAuditStatus())
                .eqIfPresent(CapacityCourseDO::getTeacherId, reqVO.getTeacherId())
                .eqIfPresent(CapacityCourseDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CapacityCourseDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CapacityCourseDO::getId));
    }

}
