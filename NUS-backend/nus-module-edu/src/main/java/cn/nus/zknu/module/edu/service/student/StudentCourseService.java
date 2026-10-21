package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;

import java.util.List;

/**
 * 学生课程 Service 接口
 */
public interface StudentCourseService {

    /**
     * 学生课程列表
     */
    List<AppStudentCourseRespVO> getCourseList(Long studentId);

    /**
     * 课程详情+资源列表
     */
    AppStudentCourseDetailRespVO getCourseDetail(Long studentId, Long courseId);

    /**
     * 等级闯关解锁状态
     */
    AppStudentUnlockStatusRespVO getUnlockStatus(Long studentId, Long courseId);

    /** 校验学生课程关系后读取资源内容。 */
    FileContentRespDTO getCourseResourceFile(Long studentId, Long resourceId);

}
