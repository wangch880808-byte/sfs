package cn.nus.zknu.module.edu.service.term;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.term.EduTermDO;

/**
 * 学期 Service 接口
 */
public interface EduTermService {

    /**
     * 创建学期
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createTerm(EduTermSaveReqVO createReqVO);

    /**
     * 更新学期
     *
     * @param updateReqVO 更新信息
     */
    void updateTerm(EduTermSaveReqVO updateReqVO);

    /**
     * 归档学期
     *
     * @param id 编号
     */
    void archiveTerm(Long id);

    /**
     * 启用学期
     *
     * @param id 编号
     */
    void enableTerm(Long id);

    /**
     * 获得学期详情
     *
     * @param id 编号
     * @return 学期
     */
    EduTermDO getTerm(Long id);

    /**
     * 获得学期分页
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduTermDO> getTermPage(EduTermPageReqVO reqVO);

}
