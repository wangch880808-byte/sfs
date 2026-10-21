package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCorrectRespVO;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewTaskStatusRespVO;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduFeedbackDO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduReviewTaskDO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduFeedbackMapper;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduReviewTaskMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewProperties;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_FEEDBACK_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_DISABLED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_TASK_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_FEEDBACK_NO_DRAFT;

@Service
@Slf4j
public class AiEduReviewServiceImpl implements AiEduReviewService {

    private static final Set<String> FEEDBACK_TYPES = Set.of("strict", "normal", "loose");
    /** 批量生成时保持不动的初评状态：处理中、已生成、教师已采纳或忽略。 */
    private static final Set<Integer> KEEP_EXISTING = Set.of(HomeworkAiReviewStatusEnum.PROCESSING,
            HomeworkAiReviewStatusEnum.READY, HomeworkAiReviewStatusEnum.ACCEPTED,
            HomeworkAiReviewStatusEnum.REJECTED);

    @Resource
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Resource
    private AiEduReviewTaskMapper reviewTaskMapper;
    @Resource
    private AiEduFeedbackMapper feedbackMapper;
    @Resource
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Resource
    private EduHomeworkAiReviewService homeworkAiReviewService;
    @Resource
    private EduAiBusinessAccessService accessService;
    @Resource
    private AiReviewCalibrationService calibrationService;
    @Resource(name = "eduAiReviewTaskExecutor")
    private Executor taskExecutor;
    @Resource
    private EduAiReviewProperties properties;

    @Override
    public AiReviewCorrectRespVO correctOne(Long homeworkRecordId) {
        accessService.requireRecordAccess(homeworkRecordId);
        long startTime = System.currentTimeMillis();
        EduHomeworkAiReviewDO review = homeworkAiReviewService.generatePreReview(homeworkRecordId, true);
        AiReviewCorrectRespVO respVO = new AiReviewCorrectRespVO();
        respVO.setScore(review.getAiScore());
        respVO.setComment(review.getAiComment());
        respVO.setCostTime((int) (System.currentTimeMillis() - startTime));
        return respVO;
    }

    @Override
    public Long correctAllAsync(Long homeworkId, Long ignoredClassId) {
        if (!properties.isEnabled()) {
            throw exception(AI_REVIEW_DISABLED);
        }
        EduHomeworkDO homework = accessService.requireHomeworkCorrectionAccess(homeworkId);
        List<EduHomeworkRecordDO> records = homeworkRecordMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId)
                        .in(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.PENDING_REVIEW));

        AiEduReviewTaskDO task = new AiEduReviewTaskDO();
        task.setHomeworkId(homeworkId);
        task.setClassId(homework.getClassId());
        task.setTeacherId(getLoginUserId());
        task.setTotalCount(records.size());
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setStatus(0);
        reviewTaskMapper.insert(task);

        try {
            taskExecutor.execute(() -> generateDrafts(task.getId()));
        } catch (org.springframework.core.task.TaskRejectedException ex) {
            reviewTaskMapper.failIfUnclaimed(task.getId(), records.size(), "AI 批量初评队列已满");
            throw exception(cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_BUSY);
        }
        return task.getId();
    }

    void generateDrafts(Long taskId) {
        AiEduReviewTaskDO task = reviewTaskMapper.selectById(taskId);
        if (task == null) return;
        String token = UUID.randomUUID().toString();
        if (reviewTaskMapper.claim(taskId, token) == 0) return;
        List<EduHomeworkRecordDO> records = homeworkRecordMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .eq(EduHomeworkRecordDO::getHomeworkId, task.getHomeworkId())
                        .in(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.PENDING_REVIEW));
        if (records.size() != value(task.getTotalCount())) {
            AiEduReviewTaskDO failed = new AiEduReviewTaskDO();
            failed.setStatus(2);
            failed.setFailCount(Math.max(0, value(task.getTotalCount()) - value(task.getSuccessCount())));
            failed.setErrorMessage("任务待评记录已变化，无法安全恢复批量初评，请重新发起");
            reviewTaskMapper.updateIfClaimed(failed, taskId, token);
            return;
        }
        int successCount = 0;
        int failCount = 0;
        for (EduHomeworkRecordDO record : records) {
            AiEduReviewTaskDO current = reviewTaskMapper.selectById(taskId);
            if (current == null || current.getStatus() != 0) return;
            try {
                EduHomeworkAiReviewDO draft = generateDraftIfNeeded(record, task.getTeacherId());
                if (draft == null || Objects.equals(draft.getStatus(), HomeworkAiReviewStatusEnum.FAILED)) {
                    throw new IllegalStateException("AI 初评未生成有效草案");
                }
                successCount++;
            } catch (Exception ex) {
                log.error("[generateDrafts] AI draft failed, taskId={}, recordId={}", taskId, record.getId(), ex);
                failCount++;
            }
            updateTaskProgress(taskId, token, successCount, failCount, null);
        }
        updateTaskProgress(taskId, token, successCount, failCount, failCount == 0 ? 1 : 2);
    }

    /**
     * 批量生成只补缺：这份提交（按提交时间区分重交）还没有初评、上次失败或被跳过时才生成；
     * 已生成、教师已处理或正在生成的保持不动；仍在排队的直接在本线程认领执行。
     */
    private EduHomeworkAiReviewDO generateDraftIfNeeded(EduHomeworkRecordDO record, Long teacherId) {
        EduHomeworkAiReviewDO latest = aiReviewMapper.selectLatestByRecordId(record.getId());
        if (latest != null && Objects.equals(latest.getSourceSubmitTime(), record.getSubmitTime())) {
            Integer status = latest.getStatus();
            if (Objects.equals(status, HomeworkAiReviewStatusEnum.QUEUED)) {
                EduHomeworkAiReviewDO processed = homeworkAiReviewService.processQueuedReview(latest.getId());
                return processed != null ? processed : latest;
            }
            if (KEEP_EXISTING.contains(status)) {
                return latest;
            }
        }
        return homeworkAiReviewService.generatePreReviewForTeacher(record.getId(), teacherId);
    }

    private void updateTaskProgress(Long taskId, String token, int successCount, int failCount, Integer status) {
        AiEduReviewTaskDO update = new AiEduReviewTaskDO();
        update.setId(taskId);
        update.setSuccessCount(successCount);
        update.setFailCount(failCount);
        update.setStatus(status);
        reviewTaskMapper.updateIfClaimed(update, taskId, token);
    }

    @Override
    public AiReviewTaskStatusRespVO getTaskStatus(Long taskId) {
        AiEduReviewTaskDO task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw exception(AI_REVIEW_TASK_NOT_EXISTS);
        }
        accessService.requireHomeworkViewAccess(task.getHomeworkId());
        AiReviewTaskStatusRespVO resp = new AiReviewTaskStatusRespVO();
        resp.setTaskId(task.getId());
        resp.setStatus(task.getStatus());
        resp.setTotalCount(task.getTotalCount());
        resp.setSuccessCount(task.getSuccessCount());
        resp.setFailCount(task.getFailCount());
        resp.setErrorMessage(task.getErrorMessage());
        int total = task.getTotalCount() != null ? task.getTotalCount() : 0;
        int done = value(task.getSuccessCount()) + value(task.getFailCount());
        resp.setProgress(total == 0 ? 100 : done * 100 / total);
        return resp;
    }

    @Override
    public void submitFeedback(Long homeworkRecordId, String feedbackType) {
        EduHomeworkRecordDO record = accessService.requireRecordAccess(homeworkRecordId);
        if (!FEEDBACK_TYPES.contains(feedbackType)) {
            throw exception(AI_REVIEW_FEEDBACK_INVALID);
        }
        AiEduFeedbackDO feedback = new AiEduFeedbackDO();
        feedback.setTeacherId(getLoginUserId());
        feedback.setHomeworkRecordId(homeworkRecordId);
        feedback.setFeedbackType(feedbackType);
        EduHomeworkAiReviewDO draft = aiReviewMapper.selectLatestScoredByRecordId(record.getId());
        if (draft == null) {
            throw exception(AI_REVIEW_FEEDBACK_NO_DRAFT);
        }
        feedback.setOriginalScore(draft.getAiScore());
        feedbackMapper.insert(feedback);
    }

    @Override
    public AiReviewCalibrationRespVO getCalibration(Long homeworkId) {
        EduHomeworkDO homework = accessService.requireHomeworkViewAccess(homeworkId);
        return calibrationService.calculate(getLoginUserId(), homework);
    }

    private int value(Integer value) {
        return value != null ? value : 0;
    }
}
