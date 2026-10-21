package cn.nus.zknu.module.edu.dal.mysql.clazz;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EduClassMapper extends BaseMapperX<EduClassDO> {

    default PageResult<EduClassDO> selectPage(EduClassPageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO));
    }

    default List<EduClassDO> selectList(EduClassPageReqVO reqVO) {
        return selectList(buildQuery(reqVO));
    }

    default LambdaQueryWrapperX<EduClassDO> buildQuery(EduClassPageReqVO reqVO) {
        return new LambdaQueryWrapperX<EduClassDO>()
                .likeIfPresent(EduClassDO::getClassName, reqVO.getClassName())
                .eqIfPresent(EduClassDO::getGradeYear, reqVO.getGradeYear())
                .eqIfPresent(EduClassDO::getDeptId, reqVO.getDeptId())
                .eqIfPresent(EduClassDO::getEducationLevel, reqVO.getEducationLevel())
                .eqIfPresent(EduClassDO::getSchoolingYear, reqVO.getSchoolingYear())
                .eqIfPresent(EduClassDO::getStatus, reqVO.getStatus())
                .eq(!Boolean.TRUE.equals(reqVO.getIncludeArchived()), EduClassDO::getStatus, 0)
                .betweenIfPresent(EduClassDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(EduClassDO::getId);
    }

    default List<EduClassDO> selectSimpleList(Integer status, Long deptId, Integer gradeYear) {
        return selectList(new LambdaQueryWrapperX<EduClassDO>()
                .eqIfPresent(EduClassDO::getStatus, status)
                .eqIfPresent(EduClassDO::getDeptId, deptId)
                .eqIfPresent(EduClassDO::getGradeYear, gradeYear)
                .orderByDesc(EduClassDO::getId));
    }

}
