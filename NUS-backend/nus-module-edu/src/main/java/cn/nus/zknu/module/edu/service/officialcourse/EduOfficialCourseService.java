package cn.nus.zknu.module.edu.service.officialcourse;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCoursePageReqVO;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCourseSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.officialcourse.EduOfficialCourseDO;

/**
 * 正式课程 Service 接口
 */
public interface EduOfficialCourseService {

    /**
     * 创建正式课程
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createOfficialCourse(EduOfficialCourseSaveReqVO createReqVO);

    /**
     * 更新正式课程
     *
     * @param updateReqVO 更新信息
     */
    void updateOfficialCourse(EduOfficialCourseSaveReqVO updateReqVO);

    /**
     * 归档正式课程
     *
     * @param id 编号
     */
    void archiveOfficialCourse(Long id);

    /**
     * 获得正式课程详情
     *
     * @param id 编号
     * @return 正式课程
     */
    EduOfficialCourseDO getOfficialCourse(Long id);

    /**
     * 获得正式课程分页
     *
     * @param reqVO 分页请求
     * @return 分页结果
     */
    PageResult<EduOfficialCourseDO> getOfficialCoursePage(EduOfficialCoursePageReqVO reqVO);

}
