package cn.nus.zknu.module.edu.dal.mysql.officialcourse;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCoursePageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.officialcourse.EduOfficialCourseDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EduOfficialCourseMapper extends BaseMapperX<EduOfficialCourseDO> {

    default PageResult<EduOfficialCourseDO> selectPage(EduOfficialCoursePageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO));
    }

    default LambdaQueryWrapperX<EduOfficialCourseDO> buildQuery(EduOfficialCoursePageReqVO reqVO) {
        return new LambdaQueryWrapperX<EduOfficialCourseDO>()
                .likeIfPresent(EduOfficialCourseDO::getName, reqVO.getName())
                .likeIfPresent(EduOfficialCourseDO::getCode, reqVO.getCode())
                .eqIfPresent(EduOfficialCourseDO::getDeptId, reqVO.getDeptId())
                .eqIfPresent(EduOfficialCourseDO::getStatus, reqVO.getStatus())
                .orderByDesc(EduOfficialCourseDO::getId);
    }

}
