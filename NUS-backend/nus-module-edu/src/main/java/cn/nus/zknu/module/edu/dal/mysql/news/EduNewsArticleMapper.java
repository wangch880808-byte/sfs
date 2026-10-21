package cn.nus.zknu.module.edu.dal.mysql.news;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.news.EduNewsArticleDO;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface EduNewsArticleMapper extends BaseMapperX<EduNewsArticleDO> {

    default PageResult<EduNewsArticleDO> selectPage(AppStudentNewsPageReqVO reqVO) {
        LambdaQueryWrapperX<EduNewsArticleDO> queryWrapper = new LambdaQueryWrapperX<EduNewsArticleDO>()
                .eqIfPresent(EduNewsArticleDO::getSourceCode, reqVO.getSourceCode());
        if (StringUtils.hasText(reqVO.getKeyword())) {
            queryWrapper.and(wrapper -> wrapper.like(EduNewsArticleDO::getTitle, reqVO.getKeyword())
                    .or()
                    .like(EduNewsArticleDO::getContentText, reqVO.getKeyword()));
        }
        return selectPage(reqVO, queryWrapper
                .orderByDesc(EduNewsArticleDO::getPublishDate)
                .orderByDesc(EduNewsArticleDO::getId));
    }

    default EduNewsArticleDO selectBySourceCodeAndUrlMd5(String sourceCode, String urlMd5) {
        return selectFirstOne(EduNewsArticleDO::getSourceCode, sourceCode,
                EduNewsArticleDO::getUrlMd5, urlMd5);
    }
}
