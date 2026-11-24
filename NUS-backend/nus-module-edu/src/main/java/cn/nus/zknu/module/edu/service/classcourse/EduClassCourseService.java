package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;

import java.util.List;

/**
 * 班级课程关联 Service 接口
 */
public interface EduClassCourseService {

    /**
     * 班级绑定课程（批量，重复跳过）
     *
     * @param bindReqVO 绑定信息
     */
    void bindClassCourse(EduClassCourseBindReqVO bindReqVO);

    /**
     * 按关联记录 ID 归档班课
     *
     * @param id 关联记录 ID
     */
    void unbindClassCourse(Long id);

    void restoreClassCourse(Long id);

    void claimClassCourse(EduClassCourseClaimReqVO request);

    void releaseClassCourse(Long id);

    List<EduClassCourseClaimOptionRespVO> claimOptions(Long courseId, String semesterName);

    /**
     * 查询班级的课程列表
     *
     * @param classId 班级 ID
     * @return 关联列表
     */
    List<EduClassCourseDO> listByClass(Long classId, boolean includeArchived);

    default List<EduClassCourseDO> listByClass(Long classId) {
        return listByClass(classId, false);
    }

    /**
     * 查询课程的班级列表
     *
     * @param courseId 课程 ID
     * @return 关联列表
     */
    List<EduClassCourseDO> listByCourse(Long courseId, boolean includeArchived);

    default List<EduClassCourseDO> listByCourse(Long courseId) {
        return listByCourse(courseId, false);
    }

    /**
     * 获取教学班运行摘要（P2-4 教学过程分析补教学班视角）
     *
     * @param classCourseId 班课关联 ID
     * @return 运行摘要（含作业完成表）
     */
    cn.nus.zknu.module.edu.controller.admin.classcourse.vo.ClassCourseProcessSummaryRespVO getProcessSummary(Long classCourseId);

}
