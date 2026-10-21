package cn.nus.zknu.module.edu.controller.admin.aireview;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.*;
import cn.nus.zknu.module.edu.service.aireview.AiEduReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - AI评阅批改")
@RestController
@RequestMapping("/edu/ai-review")
@Validated
public class AiEduReviewController {

    @Resource
    private AiEduReviewService aiEduReviewService;

    @GetMapping("/calibration")
    @Operation(summary = "查询当前教师的 AI 评分校准状态")
    @PreAuthorize("@ss.hasPermission('edu:ai-review:correct')")
    public CommonResult<AiReviewCalibrationRespVO> getCalibration(@RequestParam("homeworkId") Long homeworkId) {
        return success(aiEduReviewService.getCalibration(homeworkId));
    }

    @PostMapping("/correct-one")
    @Operation(summary = "单份AI初评草案生成(同步)")
    @PreAuthorize("@ss.hasPermission('edu:ai-review:correct')")
    public CommonResult<AiReviewCorrectRespVO> correctOne(@Valid @RequestBody AiReviewCorrectReqVO reqVO) {
        return success(aiEduReviewService.correctOne(reqVO.getHomeworkRecordId()));
    }

    @PostMapping("/correct-all-async")
    @Operation(summary = "批量AI初评草案生成(异步)")
    @PreAuthorize("@ss.hasPermission('edu:ai-review:correct')")
    public CommonResult<AiTaskStartRespVO> correctAllAsync(@Valid @RequestBody AiReviewCorrectAllReqVO reqVO) {
        return success(new AiTaskStartRespVO(
                aiEduReviewService.correctAllAsync(reqVO.getHomeworkId(), reqVO.getClassId())));
    }

    @GetMapping("/task-status")
    @Operation(summary = "查询批改任务状态")
    @Parameter(name = "taskId", description = "任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('edu:ai-review:correct')")
    public CommonResult<AiReviewTaskStatusRespVO> getTaskStatus(@RequestParam("taskId") Long taskId) {
        return success(aiEduReviewService.getTaskStatus(taskId));
    }

    @PostMapping("/feedback")
    @Operation(summary = "AI评分反馈")
    @PreAuthorize("@ss.hasPermission('edu:ai-review:correct')")
    public CommonResult<Boolean> submitFeedback(@Valid @RequestBody AiReviewFeedbackReqVO reqVO) {
        aiEduReviewService.submitFeedback(reqVO.getHomeworkRecordId(), reqVO.getFeedbackType());
        return success(true);
    }

}
