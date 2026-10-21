package cn.nus.zknu.module.edu.service.coursemanage;

import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageClassDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageTeacherMessageReqVO;

/**
 * 课程管理详情聚合 Service
 */
public interface EduCourseManageService {

    /**
     * 获取课程详情页数据（课程信息 + 班级 + 任务 + 资源）
     */
    CourseManageDetailRespVO getCourseDetail(Long courseId);

    /**
     * 获取班级参与详情页数据
     */
    CourseManageClassDetailRespVO getClassDetail(Long classCourseId);

    /**
     * 教师发送留言
     */
    void sendTeacherMessage(CourseManageTeacherMessageReqVO reqVO);
}
