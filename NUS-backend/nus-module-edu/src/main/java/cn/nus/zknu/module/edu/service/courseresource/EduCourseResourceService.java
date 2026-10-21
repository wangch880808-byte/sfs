package cn.nus.zknu.module.edu.service.courseresource;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourcePageReqVO;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourceSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;

/**
 * 课程资源 Service 接口
 */
public interface EduCourseResourceService {

    /**
     * 上传课程资源
     *
     * @param createReqVO 资源信息
     * @return 资源 ID
     */
    Long createResource(EduCourseResourceSaveReqVO createReqVO);

    /**
     * 删除课程资源（软删除）
     *
     * @param id 资源 ID
     */
    void deleteResource(Long id);

    /**
     * 获取课程资源详情
     *
     * @param id 资源 ID
     * @return 资源
     */
    EduCourseResourceDO getResource(Long id);

    /**
     * 分页查询课程资源
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduCourseResourceDO> getResourcePage(EduCourseResourcePageReqVO reqVO);

    /**
     * 获取课程资源的文本预览内容
     *
     * @param id 资源 ID
     * @return 文本预览内容
     */
    String getResourcePreviewText(Long id);

}
