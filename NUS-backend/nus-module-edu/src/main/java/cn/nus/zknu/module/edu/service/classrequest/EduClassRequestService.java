package cn.nus.zknu.module.edu.service.classrequest;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestRespVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestSaveReqVO;

/**
 * 建班申请 Service 接口
 */
public interface EduClassRequestService {

    /**
     * 教师提交建班申请
     *
     * @param reqVO 申请信息
     * @return 申请 ID
     */
    Long submitClassRequest(EduClassRequestSaveReqVO reqVO);

    /**
     * 分页查询建班申请
     *
     * @param reqVO 分页请求（status / courseId / applicantId 筛选，按创建时间降序）
     * @return 分页结果（含课程、班级、申请人等冗余字段）
     */
    PageResult<EduClassRequestRespVO> getClassRequestPage(EduClassRequestPageReqVO reqVO);

    /**
     * 获取建班申请详情
     *
     * @param id 申请 ID
     * @return 申请详情
     */
    EduClassRequestRespVO getClassRequest(Long id);

    /**
     * 审核通过：置为通过并创建班课关联
     *
     * @param id 申请 ID
     */
    void approveClassRequest(Long id);

    /**
     * 审核拒绝
     *
     * @param id     申请 ID
     * @param reason 拒绝原因
     */
    void rejectClassRequest(Long id, String reason);

}
