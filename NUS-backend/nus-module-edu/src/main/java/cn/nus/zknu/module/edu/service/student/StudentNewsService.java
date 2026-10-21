package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentNewsSourceRespVO;

import java.util.List;

/**
 * 学生校园资讯 Service
 */
public interface StudentNewsService {

    /**
     * 资讯来源列表
     */
    List<AppStudentNewsSourceRespVO> getNewsSources();

    /**
     * 资讯分页列表
     */
    PageResult<AppStudentNewsRespVO> getNewsPage(AppStudentNewsPageReqVO reqVO);

    /**
     * 资讯详情
     */
    AppStudentNewsDetailRespVO getNews(Long id);

}
