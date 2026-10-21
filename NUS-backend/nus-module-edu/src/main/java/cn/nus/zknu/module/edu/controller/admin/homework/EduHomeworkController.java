package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiTaskStartRespVO;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRemoveService;
import cn.nus.zknu.module.edu.service.homework.HomeworkWriteAccess;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;
import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 实训任务管理")
@RestController
@RequestMapping("/edu/homework")
@Validated
public class EduHomeworkController {

    @Resource
    private EduHomeworkService eduHomeworkService;
    @Resource
    private EduHomeworkRemoveService removeService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    /**
     * 创建实训任务。
     */
    @PostMapping("/create")
    @Operation(summary = "创建实训任务")
    @PreAuthorize("@ss.hasPermission('edu:homework:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "实训任务正在提交中，请勿重复操作")
    public CommonResult<Long> createHomework(@Valid @RequestBody EduHomeworkSaveReqVO createReqVO) {
        return success(eduHomeworkService.createHomework(createReqVO));
    }

    @PostMapping("/ai-create/preview-task")
    @Operation(summary = "AI新建任务 - 启动异步题目预览")
    @PreAuthorize("@ss.hasPermission('edu:homework:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "AI 新建任务预览正在生成中，请勿重复操作")
    public CommonResult<AiTaskStartRespVO> startAiCreateHomeworkPreviewTask(
            @Valid @RequestBody EduHomeworkAiCreatePreviewReqVO reqVO) {
        return success(new AiTaskStartRespVO(eduHomeworkService.startAiCreateHomeworkPreviewTask(reqVO)));
    }

    @GetMapping("/ai-create/preview-task/status")
    @Operation(summary = "AI新建任务 - 查询异步题目预览状态")
    @PreAuthorize("@ss.hasPermission('edu:homework:create')")
    public CommonResult<EduHomeworkAiCreateTaskStatusRespVO> getAiCreateHomeworkPreviewTaskStatus(
            @RequestParam("taskId") Long taskId) {
        return success(eduHomeworkService.getAiCreateHomeworkPreviewTaskStatus(taskId));
    }

    @PostMapping("/ai-create/regenerate")
    @Operation(summary = "AI新建任务 - 单题重新生成")
    @PreAuthorize("@ss.hasPermission('edu:homework:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "AI 新建任务题目正在重新生成中，请勿重复操作")
    public CommonResult<EduHomeworkAiCreatePreviewRespVO.Item> regenerateAiCreateHomeworkQuestion(
            @Valid @RequestBody EduHomeworkAiCreateRegenerateReqVO reqVO) {
        return success(eduHomeworkService.regenerateAiCreateHomeworkQuestion(reqVO));
    }

    @PostMapping("/ai-create/confirm")
    @Operation(summary = "AI新建任务 - 确认创建实训任务")
    @PreAuthorize("@ss.hasPermission('edu:homework:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "正在新建任务，请勿重复操作")
    public CommonResult<EduHomeworkAiCreateConfirmRespVO> confirmAiCreateHomework(
            @Valid @RequestBody EduHomeworkAiCreateConfirmReqVO reqVO) {
        return success(eduHomeworkService.confirmAiCreateHomework(reqVO));
    }

    /**
     * 更新实训任务。
     */
    @PutMapping("/update")
    @Operation(summary = "更新实训任务")
    @PreAuthorize("@ss.hasPermission('edu:homework:update')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "实训任务正在处理中，请勿重复操作")
    public CommonResult<Boolean> updateHomework(@Valid @RequestBody EduHomeworkSaveReqVO updateReqVO) {
        eduHomeworkService.updateHomework(updateReqVO);
        return success(true);
    }

    /**
     * 归档实训任务，保留提交记录。
     */
    @DeleteMapping("/delete")
    @Operation(summary = "封存实训任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:homework:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "实训任务正在封存中，请勿重复操作")
    public CommonResult<Boolean> deleteHomework(@RequestParam("id") Long id) {
        eduHomeworkService.deleteHomework(id);
        return success(true);
    }

    @GetMapping("/remove-impact")
    @Operation(summary = "查询删除任务的影响")
    @PreAuthorize("@ss.hasPermission('edu:homework:delete')")
    public CommonResult<EduHomeworkRemoveImpactRespVO> getRemoveImpact(
            @RequestParam("ids") @NotEmpty @Size(max = 100) List<Long> ids) {
        return success(removeService.getRemoveImpact(ids));
    }

    @DeleteMapping("/remove")
    @Operation(summary = "删除实训任务及从属数据")
    @PreAuthorize("@ss.hasPermission('edu:homework:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "实训任务正在删除中，请勿重复操作")
    public CommonResult<Boolean> removeHomework(@RequestParam("id") Long id) {
        removeService.removeHomeworks(List.of(id));
        return success(true);
    }

    @DeleteMapping("/remove-list")
    @Operation(summary = "批量删除实训任务及从属数据")
    @PreAuthorize("@ss.hasPermission('edu:homework:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "实训任务正在删除中，请勿重复操作")
    public CommonResult<Boolean> removeHomeworkList(
            @RequestParam("ids") @NotEmpty @Size(max = 100) List<Long> ids) {
        removeService.removeHomeworks(ids);
        return success(true);
    }

    @PutMapping("/restore")
    @Operation(summary = "恢复实训任务")
    @PreAuthorize("@ss.hasPermission('edu:homework:delete')")
    public CommonResult<Boolean> restoreHomework(@RequestParam("id") Long id) {
        eduHomeworkService.restoreHomework(id);
        return success(true);
    }

    /**
     * 获取实训任务详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获取实训任务详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:homework:query')")
    public CommonResult<EduHomeworkRespVO> getHomework(@RequestParam("id") Long id) {
        EduHomeworkDO homework = eduHomeworkService.getHomework(id);
        EduHomeworkRespVO response = BeanUtils.toBean(homework, EduHomeworkRespVO.class);
        response.setCanGrade(HomeworkWriteAccess.canGrade(homework)
                && securityFrameworkService.hasPermission("edu:homework-record:correct"));
        return success(response);
    }

    /**
     * 分页查询实训任务。
     */
    @GetMapping("/page")
    @Operation(summary = "获取实训任务分页")
    @PreAuthorize("@ss.hasPermission('edu:homework:query')")
    public CommonResult<PageResult<EduHomeworkRespVO>> getHomeworkPage(@Validated EduHomeworkPageReqVO pageReqVO) {
        PageResult<EduHomeworkDO> pageResult = eduHomeworkService.getHomeworkPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, EduHomeworkRespVO.class));
    }

    /**
     * 获取任务统计信息。
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取任务统计")
    @Parameter(name = "homeworkId", description = "任务 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:homework:query')")
    public CommonResult<EduHomeworkStatisticsRespVO> getHomeworkStatistics(@RequestParam("homeworkId") Long homeworkId) {
        return success(eduHomeworkService.getHomeworkStatistics(homeworkId));
    }

}
