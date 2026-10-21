package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.collection.CollectionUtils;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordPageMapper;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 任务提交与批改")
@RestController
@RequestMapping("/edu/homework-record")
@Validated
public class EduHomeworkRecordController {

    @Resource
    private EduHomeworkRecordService eduHomeworkRecordService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduHomeworkRecordPageMapper recordPageMapper;
    @Resource
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Resource
    private EduHomeworkAiReviewService eduHomeworkAiReviewService;

    /**
     * 提交任务。
     */
    @PostMapping("/submit")
    @Operation(summary = "提交任务")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:submit')")
    public CommonResult<Long> submitRecord(@Valid @RequestBody EduHomeworkRecordSubmitReqVO submitReqVO) {
        return success(eduHomeworkRecordService.submitRecord(submitReqVO, getLoginUserId()));
    }

    /**
     * 更新任务。
     */
    @PutMapping("/update")
    @Operation(summary = "更新任务")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:submit')")
    public CommonResult<Boolean> updateRecord(@Valid @RequestBody EduHomeworkRecordUpdateReqVO updateReqVO) {
        eduHomeworkRecordService.updateRecord(updateReqVO, getLoginUserId());
        return success(true);
    }

    /**
     * 获取提交详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获取提交详情")
    @Parameter(name = "id", description = "记录 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:query')")
    public CommonResult<EduHomeworkRecordRespVO> getRecord(@RequestParam("id") Long id) {
        EduHomeworkRecordDO record = eduHomeworkRecordService.getRecord(id);
        return success(fillStudentInfo(BeanUtils.toBean(record, EduHomeworkRecordRespVO.class)));
    }

    @GetMapping("/history")
    @Operation(summary = "查看提交与评分轨迹")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<List<EduHomeworkRecordHistoryRespVO>> getHistory(@RequestParam("recordId") Long recordId) {
        return success(eduHomeworkRecordService.getHistory(recordId));
    }

    /**
     * 分页查询任务记录。
     */
    @GetMapping("/page")
    @Operation(summary = "获取任务记录分页")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:query')")
    public CommonResult<PageResult<EduHomeworkRecordRespVO>> getRecordPage(@Validated EduHomeworkRecordPageReqVO pageReqVO) {
        PageResult<EduHomeworkRecordDO> pageResult = eduHomeworkRecordService.getRecordPage(pageReqVO);
        return success(fillStudentInfo(BeanUtils.toBean(pageResult, EduHomeworkRecordRespVO.class)));
    }

    /**
     * 待批改列表。
     */
    @GetMapping("/correct-page")
    @Operation(summary = "获取待批改列表")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<PageResult<EduHomeworkRecordRespVO>> getCorrectPage(@Validated EduHomeworkRecordPageReqVO pageReqVO) {
        PageResult<EduHomeworkRecordDO> pageResult = eduHomeworkRecordService.getCorrectPage(pageReqVO);
        return success(fillStudentInfo(BeanUtils.toBean(pageResult, EduHomeworkRecordRespVO.class)));
    }

    /**
     * 批改队列：任务下全部可批改的提交，不分页。
     */
    @GetMapping("/grading-queue")
    @Operation(summary = "获取批改队列")
    @Parameter(name = "homeworkId", description = "任务 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<EduHomeworkRecordQueueRespVO> getGradingQueue(@RequestParam("homeworkId") Long homeworkId) {
        return success(eduHomeworkRecordService.getGradingQueue(homeworkId));
    }

    /**
     * 批改任务。
     */
    @PutMapping("/correct")
    @Operation(summary = "批改任务")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "批改正在保存，请勿重复操作")
    public CommonResult<EduHomeworkRecordGradeRespVO> correctRecord(
            @Valid @RequestBody EduHomeworkRecordCorrectReqVO correctReqVO) {
        return success(eduHomeworkRecordService.correctRecord(correctReqVO));
    }

    /**
     * 打回重做（单个）。
     */
    @PutMapping("/redo")
    @Operation(summary = "打回重做")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "打回正在处理，请勿重复操作")
    public CommonResult<EduHomeworkRecordGradeRespVO> redoRecord(@Valid @RequestBody EduHomeworkRecordRedoReqVO reqVO) {
        return success(eduHomeworkRecordService.redoRecord(reqVO.getId(), reqVO.getRedoDeadline(),
                reqVO.getExpectedUpdateTime()));
    }

    /**
     * 撤销批改或打回（撤销期内）。
     */
    @PutMapping("/undo")
    @Operation(summary = "撤销批改或打回")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "撤销正在处理，请勿重复操作")
    public CommonResult<EduHomeworkRecordGradeRespVO> undoGrading(@Valid @RequestBody EduHomeworkRecordUndoReqVO reqVO) {
        return success(eduHomeworkRecordService.undoGrading(reqVO.getUndoToken()));
    }

    /**
     * 批量打回重做。
     */
    @PutMapping("/redo-batch")
    @Operation(summary = "批量打回重做")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:correct')")
    public CommonResult<Boolean> redoRecordBatch(@Valid @RequestBody EduHomeworkRecordRedoBatchReqVO reqVO) {
        eduHomeworkRecordService.redoRecordBatch(reqVO.getIds(), reqVO.getRedoDeadline());
        return success(true);
    }

    private PageResult<EduHomeworkRecordRespVO> fillStudentInfo(PageResult<EduHomeworkRecordRespVO> pageResult) {
        if (pageResult == null || pageResult.getList() == null || pageResult.getList().isEmpty()) {
            return pageResult;
        }
        Map<Long, AdminUserDO> userMap = adminUserService.getUserMap(
                CollectionUtils.convertSet(pageResult.getList(), EduHomeworkRecordRespVO::getStudentId));
        Map<Long, Map<Long, RankInfo>> rankMap = buildRankMap(
                CollectionUtils.convertSet(pageResult.getList(), EduHomeworkRecordRespVO::getHomeworkId));
        Map<Long, EduHomeworkAiReviewDO> reviews = CollectionUtils.convertMap(
                aiReviewMapper.selectLatestByRecordIds(CollectionUtils.convertSet(
                        pageResult.getList(), EduHomeworkRecordRespVO::getId)), EduHomeworkAiReviewDO::getHomeworkRecordId);
        pageResult.getList().forEach(item -> {
            fillStudentInfo(item, userMap);
            fillAiReviewInfo(item, reviews.get(item.getId()));
            fillRankInfo(item, rankMap.get(item.getHomeworkId()));
        });
        return pageResult;
    }

    private EduHomeworkRecordRespVO fillStudentInfo(EduHomeworkRecordRespVO respVO) {
        if (respVO == null || respVO.getStudentId() == null) {
            return respVO;
        }
        fillStudentInfo(respVO, adminUserService.getUserMap(List.of(respVO.getStudentId())));
        fillAiReviewInfo(respVO, eduHomeworkAiReviewService.getLatestByRecordId(respVO.getId()));
        fillRankInfo(respVO, buildRankMap(List.of(respVO.getHomeworkId())).get(respVO.getHomeworkId()));
        return respVO;
    }

    private EduHomeworkRecordRespVO fillStudentInfo(EduHomeworkRecordRespVO respVO, Map<Long, AdminUserDO> userMap) {
        AdminUserDO user = userMap.get(respVO.getStudentId());
        if (user != null) {
            respVO.setStudentName(user.getNickname());
            respVO.setStudentNo(user.getUsername());
        }
        return respVO;
    }

    private void fillRankInfo(EduHomeworkRecordRespVO respVO, Map<Long, RankInfo> rankInfoMap) {
        if (respVO == null || respVO.getStatus() == null || respVO.getStatus() != EduHomeworkRecordStatusEnum.CORRECTED
                || respVO.getStudentId() == null || rankInfoMap == null) {
            return;
        }
        RankInfo rankInfo = rankInfoMap.get(respVO.getStudentId());
        if (rankInfo != null) {
            respVO.setClassRank(rankInfo.rank());
            respVO.setRankTotalStudents(rankInfo.total());
        }
    }

    private void fillAiReviewInfo(EduHomeworkRecordRespVO respVO, EduHomeworkAiReviewDO review) {
        if (respVO == null || respVO.getId() == null) {
            return;
        }
        if (review == null) {
            return;
        }
        respVO.setAiReviewStatus(review.getStatus());
        respVO.setAiReviewScore(review.getAiScore());
        respVO.setAiReviewComment(review.getAiComment());
        respVO.setAiReviewConfidence(review.getAiConfidence());
    }

    private Map<Long, Map<Long, RankInfo>> buildRankMap(Iterable<Long> homeworkIds) {
        Map<Long, Map<Long, RankInfo>> result = new java.util.HashMap<>();
        if (homeworkIds == null) {
            return result;
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        homeworkIds.forEach(id -> { if (id != null) ids.add(id); });
        if (ids.isEmpty()) {
            return result;
        }
        for (EduHomeworkRecordPageMapper.RankRow row : recordPageMapper.selectRanks(ids)) {
            result.computeIfAbsent(row.getHomeworkId(), ignored -> new java.util.HashMap<>())
                    .put(row.getStudentId(), new RankInfo(row.getRank(), row.getTotal()));
        }
        return result;
    }

    private record RankInfo(int rank, int total) {
    }

}
