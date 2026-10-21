package cn.nus.zknu.module.capacity.service.course;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseAuditReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCoursePageReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CapacityCourseService {

    Long createCourse(CapacityCourseSaveReqVO createReqVO, MultipartFile courseImageFile) throws Exception;

    void updateCourse(CapacityCourseSaveReqVO updateReqVO, MultipartFile courseImageFile) throws Exception;

    void deleteCourse(Long id);

    void deleteCourseList(List<Long> ids);

    void restoreCourse(Long id);

    void restoreCourseList(List<Long> ids);

    CapacityCourseDO getCourse(Long id);

    PageResult<CapacityCourseDO> getCoursePage(CapacityCoursePageReqVO reqVO);

    void auditPublicCourse(CapacityCourseAuditReqVO reqVO);

}
