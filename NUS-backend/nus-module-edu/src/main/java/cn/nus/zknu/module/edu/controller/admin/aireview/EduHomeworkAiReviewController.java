package cn.nus.zknu.module.edu.controller.admin.aireview;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.EduHomeworkAiReviewRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 作业AI初评")
@RestController
@RequestMapping("/edu/homework-ai-review")
@Validated
public class EduHomeworkAiReviewController {

    @Resource
    private EduHomeworkAiReviewService eduHomeworkAiReviewService;

    @GetMapping("/get-by-record")
    @Operation(summary = "根据任务记录获取AI初评草案")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<EduHomeworkAiReviewRespVO> getByRecord(
            @RequestParam("homeworkRecordId")
            @Parameter(description = "任务记录ID", required = true) Long homeworkRecordId) {
        EduHomeworkAiReviewDO review = eduHomeworkAiReviewService.getLatestByRecordId(homeworkRecordId);
        EduHomeworkAiReviewRespVO respVO = BeanUtils.toBean(review, EduHomeworkAiReviewRespVO.class);
        if (respVO != null) {
            respVO.setStudentCommentSuggestion(
                    eduHomeworkAiReviewService.buildStudentCommentSuggestion(homeworkRecordId));
            respVO.setConfidenceThreshold(
                    eduHomeworkAiReviewService.getConfidenceThreshold(homeworkRecordId));
        }
        return success(respVO);
    }

    @PostMapping("/trigger")
    @Operation(summary = "手动触发AI初评草案生成")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<Boolean> trigger(@RequestParam("homeworkRecordId") Long homeworkRecordId) {
        eduHomeworkAiReviewService.triggerPreReviewAsync(homeworkRecordId, true);
        return success(true);
    }

    @PutMapping("/apply")
    @Operation(summary = "兼容接口：AI草案不得直接形成最终成绩")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<Boolean> apply(@RequestParam("homeworkRecordId") Long homeworkRecordId) {
        eduHomeworkAiReviewService.applyLatestReview(homeworkRecordId);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "拒绝AI初评草案")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<Boolean> reject(@RequestParam("homeworkRecordId") Long homeworkRecordId) {
        eduHomeworkAiReviewService.rejectLatestReview(homeworkRecordId);
        return success(true);
    }
}
