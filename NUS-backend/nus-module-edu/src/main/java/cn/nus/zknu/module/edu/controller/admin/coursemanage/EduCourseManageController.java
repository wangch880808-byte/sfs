package cn.nus.zknu.module.edu.controller.admin.coursemanage;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageClassDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageTeacherMessageReqVO;
import cn.nus.zknu.module.edu.service.coursemanage.EduCourseManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 实训课程详情聚合")
@RestController
@RequestMapping("/edu/course-manage")
@Validated
public class EduCourseManageController {

    @Resource
    private EduCourseManageService eduCourseManageService;

    @GetMapping("/detail")
    @Operation(summary = "获取课程详情页数据（课程信息、班级、任务、资源）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:query')")
    public CommonResult<CourseManageDetailRespVO> getCourseDetail(@RequestParam("courseId") Long courseId) {
        return success(eduCourseManageService.getCourseDetail(courseId));
    }

    @GetMapping("/class-detail")
    @Operation(summary = "获取班级参与详情页数据（任务、成员、成绩、留言）")
    @Parameter(name = "classCourseId", description = "班课关联 ID", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:query')")
    public CommonResult<CourseManageClassDetailRespVO> getClassDetail(
            @RequestParam("classCourseId") Long classCourseId) {
        return success(eduCourseManageService.getClassDetail(classCourseId));
    }

    @PostMapping("/message/send-teacher")
    @Operation(summary = "课程班级留言 - 教师发送消息")
    // 课程教师在班级详情中给学生留言；服务层校验课程、班课与学生归属
    @PreAuthorize("@ss.hasAnyPermissions('edu:message:manage', 'capacity:indicator:query')")
    public CommonResult<Boolean> sendTeacherMessage(@Valid @RequestBody CourseManageTeacherMessageReqVO reqVO) {
        eduCourseManageService.sendTeacherMessage(reqVO);
        return success(true);
    }
}
