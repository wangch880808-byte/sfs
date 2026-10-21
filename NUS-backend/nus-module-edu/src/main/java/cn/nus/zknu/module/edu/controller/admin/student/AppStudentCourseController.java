package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.service.student.StudentCourseService;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.io.IOException;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.infra.framework.file.core.utils.FileTypeUtils.writeAttachmentHeaders;

@Tag(name = "学生端 - 课程")
@RestController
@RequestMapping("/edu/student/course")
@Validated
public class AppStudentCourseController {

    @Resource
    private StudentCourseService studentCourseService;
    @Resource
    private FileApi fileApi;

    @GetMapping("/list")
    @Operation(summary = "我的课程列表")
    public CommonResult<List<AppStudentCourseRespVO>> getCourseList() {
        return success(studentCourseService.getCourseList(getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "课程详情")
    @Parameter(name = "courseId", description = "课程ID", required = true, example = "1")
    public CommonResult<AppStudentCourseDetailRespVO> getCourseDetail(@RequestParam("courseId") Long courseId) {
        return success(studentCourseService.getCourseDetail(getLoginUserId(), courseId));
    }

    @GetMapping("/unlock-status")
    @Operation(summary = "等级解锁状态")
    @Parameter(name = "courseId", description = "课程ID", required = true, example = "1")
    public CommonResult<AppStudentUnlockStatusRespVO> getUnlockStatus(@RequestParam("courseId") Long courseId) {
        return success(studentCourseService.getUnlockStatus(getLoginUserId(), courseId));
    }

    @GetMapping("/resource/download")
    @Operation(summary = "下载我的课程资源")
    public void downloadCourseResource(@RequestParam("id") Long resourceId,
                                       HttpServletResponse response) throws IOException {
        FileContentRespDTO file = studentCourseService.getCourseResourceFile(getLoginUserId(), resourceId);
        writeAttachmentHeaders(response, file.getName(), file.getType(), file.getSize());
        if (!fileApi.writeFile(file.getUrl(), response.getOutputStream())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

}
