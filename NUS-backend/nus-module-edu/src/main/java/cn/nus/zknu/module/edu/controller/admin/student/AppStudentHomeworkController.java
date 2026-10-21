package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.student.vo.*;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordHistoryRespVO;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import cn.nus.zknu.module.edu.service.student.StudentHomeworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.infra.framework.file.core.utils.FileTypeUtils.writeAttachmentHeaders;
import cn.nus.zknu.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import cn.nus.zknu.module.infra.api.file.FileApi;

@Tag(name = "学生端 - 任务")
@RestController
@RequestMapping("/edu/student/homework")
@Validated
public class AppStudentHomeworkController {

    @Resource
    private StudentHomeworkService studentHomeworkService;
    @Resource
    private EduHomeworkRecordService homeworkRecordService;
    @Resource
    private FileApi fileApi;

    @GetMapping("/list")
    @Operation(summary = "任务列表")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-homework:query')")
    public CommonResult<PageResult<AppStudentHomeworkRespVO>> getHomeworkList(@Validated AppStudentHomeworkPageReqVO reqVO) {
        return success(studentHomeworkService.getHomeworkList(getLoginUserId(), reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "任务详情")
    @Parameter(name = "homeworkId", description = "任务ID", required = true, example = "1")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-homework:query')")
    public CommonResult<AppStudentHomeworkDetailRespVO> getHomeworkDetail(@RequestParam("homeworkId") Long homeworkId) {
        return success(studentHomeworkService.getHomeworkDetail(getLoginUserId(), homeworkId));
    }

    @GetMapping("/history")
    @Operation(summary = "查看本人提交与评分轨迹")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-homework:query')")
    public CommonResult<List<EduHomeworkRecordHistoryRespVO>> getHistory(@RequestParam("homeworkId") Long homeworkId) {
        return success(homeworkRecordService.getStudentHistory(homeworkId, getLoginUserId()));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交任务")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-homework:submit')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "正在提交，请勿重复提交")
    public CommonResult<Long> submitHomework(@Valid @RequestBody AppStudentHomeworkSubmitReqVO req) {
        return success(studentHomeworkService.submitHomework(getLoginUserId(), req));
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-homework:submit')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "正在提交，请勿重复提交")
    public CommonResult<Boolean> updateHomework(@Valid @RequestBody AppStudentHomeworkUpdateReqVO req) {
        studentHomeworkService.updateHomework(getLoginUserId(), req);
        return success(true);
    }

    @PostMapping("/attachment/upload")
    @Operation(summary = "上传作业附件")
    @PreAuthorize("@ss.hasRole('student') and @ss.hasPermission('edu:student-homework:submit')")
    public CommonResult<String> uploadAttachment(@RequestParam("homeworkId") Long homeworkId,
                                                 @RequestParam("file") MultipartFile file) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(file.getInputStream())) {
            input.mark(1024 * 1024);
            String detectedType = FileTypeUtils.getMineType(input, file.getOriginalFilename());
            input.reset();
            return success(studentHomeworkService.uploadAttachment(getLoginUserId(), homeworkId,
                    input, file.getSize(), file.getOriginalFilename(), detectedType));
        }
    }

    @GetMapping("/attachment/download")
    @Operation(summary = "授权下载作业附件")
    @PreAuthorize("@ss.hasRole('student') or @ss.hasPermission('edu:homework-record:query')")
    public void downloadAttachment(@RequestParam("url") String url, HttpServletResponse response) throws Exception {
        FileContentRespDTO file = studentHomeworkService.getDownloadableAttachment(getLoginUserId(), url);
        writeAttachmentHeaders(response, file.getName(), file.getType(), file.getSize());
        if (!fileApi.writeFile(file.getUrl(), response.getOutputStream())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

}
