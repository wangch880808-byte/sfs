package cn.nus.zknu.module.edu.dal.mysql.term;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.term.EduTermDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EduTermMapper extends BaseMapperX<EduTermDO> {

    default PageResult<EduTermDO> selectPage(EduTermPageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO));
    }

    default LambdaQueryWrapperX<EduTermDO> buildQuery(EduTermPageReqVO reqVO) {
        return new LambdaQueryWrapperX<EduTermDO>()
                .likeIfPresent(EduTermDO::getName, reqVO.getName())
                .likeIfPresent(EduTermDO::getCode, reqVO.getCode())
                .eqIfPresent(EduTermDO::getOrganizationId, reqVO.getOrganizationId())
                .eqIfPresent(EduTermDO::getStatus, reqVO.getStatus())
                .orderByDesc(EduTermDO::getId);
    }

}
