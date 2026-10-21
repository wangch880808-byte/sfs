package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionSetReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionStudentRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRemindRespVO;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkExtensionService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRemindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

/** 实训任务的个别延期与催交，均只允许任务的发布教师操作。 */
@Tag(name = "管理后台 - 实训任务个别延期与催交")
@RestController
@RequestMapping("/edu/homework")
@Validated
public class EduHomeworkDeadlineController {

    @Resource
    private EduHomeworkExtensionService extensionService;
    @Resource
    private EduHomeworkRemindService remindService;

    @GetMapping("/extension/list")
    @Operation(summary = "获得任务的个别延期")
    @Parameter(name = "homeworkId", description = "任务 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:homework:query')")
    public CommonResult<List<EduHomeworkExtensionRespVO>> listExtensions(@RequestParam("homeworkId") Long homeworkId) {
        return success(extensionService.listExtensions(homeworkId));
    }

    @GetMapping("/extension/students")
    @Operation(summary = "获得任务的学生名单（含提交状态与个人截止时间）")
    @Parameter(name = "homeworkId", description = "任务 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:homework:extend')")
    public CommonResult<List<EduHomeworkExtensionStudentRespVO>> listStudents(
            @RequestParam("homeworkId") Long homeworkId) {
        return success(extensionService.listStudents(homeworkId));
    }

    @PutMapping("/extension/set")
    @Operation(summary = "为学生设置个别延期")
    @PreAuthorize("@ss.hasPermission('edu:homework:extend')")
    @Idempotent(timeout = 10, timeUnit = TimeUnit.SECONDS,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "个别延期正在保存，请勿重复操作")
    public CommonResult<Integer> setExtensions(@Valid @RequestBody EduHomeworkExtensionSetReqVO reqVO) {
        return success(extensionService.setExtensions(reqVO));
    }

    @DeleteMapping("/extension/delete")
    @Operation(summary = "移除个别延期")
    @Parameter(name = "id", description = "延期 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:homework:extend')")
    public CommonResult<Boolean> deleteExtension(@RequestParam("id") Long id) {
        extensionService.deleteExtension(id);
        return success(true);
    }

    @PostMapping("/remind")
    @Operation(summary = "催交未提交的学生")
    @Parameter(name = "homeworkId", description = "任务 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:homework:remind')")
    public CommonResult<EduHomeworkRemindRespVO> remindUnsubmitted(@RequestParam("homeworkId") Long homeworkId) {
        return success(remindService.remindUnsubmitted(homeworkId));
    }

}
