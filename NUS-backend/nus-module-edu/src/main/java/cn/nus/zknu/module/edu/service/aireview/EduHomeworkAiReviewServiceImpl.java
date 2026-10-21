package cn.nus.zknu.module.edu.service.aireview;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduReviewReqDTO;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduReviewRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewProperties;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.support.ReviewEvidence;
import cn.nus.zknu.module.edu.service.aireview.support.StudentVisibleCommentFormatter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_DIRECT_APPLY_DISABLED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_DISABLED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_RECORD_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_STATUS_INVALID;

@Service
@Slf4j
public class EduHomeworkAiReviewServiceImpl implements EduHomeworkAiReviewService {

    @Resource
    private EduAiReviewProperties properties;
    @Resource
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Resource
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private HomeworkReviewEvidenceService reviewEvidenceService;
    @Resource
    private AiEduReviewApi aiEduReviewApi;
    @Resource
    private StudentVisibleCommentFormatter studentVisibleCommentFormatter;
    @Resource
    private EduAiBusinessAccessService accessService;
    @Resource
    private CapacityIndicatorApi capacityIndicatorApi;
    @Resource
    private AiReviewCalibrationService calibrationService;
    @Resource
    private cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy metricPolicy;
    @Resource
    private HomeworkAiReviewDispatcher dispatcher;
    @Resource
    private HomeworkAiReviewFailureNotifier failureNotifier;

    /** 同一份提交已有这些状态的初评时，不再重复排队。 */
    private static final List<Integer> PENDING_OR_READY = List.of(HomeworkAiReviewStatusEnum.QUEUED,
            HomeworkAiReviewStatusEnum.PROCESSING, HomeworkAiReviewStatusEnum.READY);

    @Override
    public void enqueuePreReview(Long homeworkRecordId) {
        if (homeworkRecordId == null) {
            return;
        }
        try {
            EduHomeworkRecordDO record = homeworkRecordMapper.selectById(homeworkRecordId);
            if (record == null) {
                log.error("[enqueuePreReview][提交记录不存在，无法排队 AI 初评 recordId={}]", homeworkRecordId);
                return;
            }
            EduHomeworkDO homework = homeworkMapper.selectById(record.getHomeworkId());
            if (homework == null || !isAutoPreReview(homework)) {
                return;
            }
            EduHomeworkAiReviewDO latest = aiReviewMapper.selectLatestByRecordId(record.getId());
            if (isCurrent(latest, record) && PENDING_OR_READY.contains(latest.getStatus())) {
                if (Objects.equals(latest.getStatus(), HomeworkAiReviewStatusEnum.QUEUED)) {
                    dispatcher.dispatchAfterCommit(latest.getId());
                }
                return;
            }
            EduHomeworkAiReviewDO review = prepareDraftRecord(homework, record, HomeworkAiReviewStatusEnum.QUEUED);
            if (!properties.isEnabled()) {
                review.setStatus(HomeworkAiReviewStatusEnum.SKIPPED);
                review.setErrorMessage(HomeworkAiReviewFailures.DISABLED_SKIPPED);
                aiReviewMapper.insert(review);
                return;
            }
            aiReviewMapper.insert(review);
            dispatcher.dispatchAfterCommit(review.getId());
        } catch (RuntimeException ex) {
            // AI 初评只是辅助，排队出错不能让学生的提交失败；教师仍可在批改页批量生成。
            log.error("[enqueuePreReview][AI 初评排队失败 recordId={}]", homeworkRecordId, ex);
        }
    }

    @Override
    public EduHomeworkAiReviewDO processQueuedReview(Long reviewId) {
        EduHomeworkAiReviewDO review = reviewId == null ? null : aiReviewMapper.selectById(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), HomeworkAiReviewStatusEnum.QUEUED)) {
            return null;
        }
        if (aiReviewMapper.claimQueued(review.getId(), review.getGenerationToken()) == 0) {
            return null;
        }
        review.setStatus(HomeworkAiReviewStatusEnum.PROCESSING);
        EduHomeworkRecordDO record = homeworkRecordMapper.selectById(review.getHomeworkRecordId());
        if (record == null) {
            return finish(review, HomeworkAiReviewFailures.failed(review.getId(),
                    HomeworkAiReviewFailures.RECORD_MISSING_CAUSE, false));
        }
        if (!EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(record.getStatus())) {
            return finish(review, HomeworkAiReviewFailures.failed(review.getId(),
                    HomeworkAiReviewFailures.WRONG_STATUS_CAUSE, false));
        }
        if (!Objects.equals(record.getSubmitTime(), review.getSourceSubmitTime())) {
            return finish(review, HomeworkAiReviewFailures.skipped(review.getId(),
                    HomeworkAiReviewFailures.SUPERSEDED_SKIPPED));
        }
        EduHomeworkDO homework = homeworkMapper.selectById(record.getHomeworkId());
        if (homework == null) {
            return finish(review, HomeworkAiReviewFailures.failed(review.getId(),
                    HomeworkAiReviewFailures.HOMEWORK_MISSING_CAUSE, false));
        }
        if (!properties.isEnabled()) {
            return finish(review, HomeworkAiReviewFailures.skipped(review.getId(),
                    HomeworkAiReviewFailures.DISABLED_SKIPPED));
        }
        try {
            return runReview(homework, record, review, homework.getTeacherId(), true);
        } catch (RuntimeException ex) {
            log.error("[processQueuedReview][AI 初评失败 reviewId={}, recordId={}]",
                    review.getId(), record.getId(), ex);
            return aiReviewMapper.selectById(review.getId());
        }
    }

    @Override
    public void triggerPreReviewAsync(Long homeworkRecordId) {
        enqueuePreReview(homeworkRecordId);
    }

    @Override
    public void triggerPreReviewAsync(Long homeworkRecordId, boolean force) {
        if (!force) {
            enqueuePreReview(homeworkRecordId);
            return;
        }
        if (!properties.isEnabled()) {
            throw exception(AI_REVIEW_DISABLED);
        }
        if (homeworkRecordId == null) {
            throw exception(AI_REVIEW_RECORD_NOT_EXISTS);
        }
        EduHomeworkRecordDO record = accessService.requireRecordAccess(homeworkRecordId);
        if (record == null || !EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(record.getStatus())) {
            throw exception(AI_REVIEW_STATUS_INVALID);
        }
        EduHomeworkDO homework = homeworkMapper.selectById(record.getHomeworkId());
        if (homework == null) {
            throw exception(AI_REVIEW_RECORD_NOT_EXISTS);
        }
        EduHomeworkAiReviewDO latest = aiReviewMapper.selectLatestByRecordId(record.getId());
        if (isCurrent(latest, record)) {
            if (Objects.equals(latest.getStatus(), HomeworkAiReviewStatusEnum.QUEUED)) {
                // 已在排队：立即再派发一次，线程池仍满则继续等待定时补派。
                dispatcher.dispatchAfterCommit(latest.getId());
                return;
            }
            if (Objects.equals(latest.getStatus(), HomeworkAiReviewStatusEnum.PROCESSING)) {
                return;
            }
        }
        EduHomeworkAiReviewDO review = prepareDraftRecord(homework, record, HomeworkAiReviewStatusEnum.QUEUED);
        aiReviewMapper.insert(review);
        dispatcher.dispatchAfterCommit(review.getId());
    }

    @Override
    public EduHomeworkAiReviewDO generatePreReview(Long homeworkRecordId, boolean force) {
        accessService.requireRecordAccess(homeworkRecordId);
        return generateNow(homeworkRecordId,
                cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), false);
    }

    /** 已由批量任务在入口校验布置者身份，后台线程不依赖请求线程的安全上下文。 */
    public EduHomeworkAiReviewDO generatePreReviewForTeacher(Long recordId, Long teacherId) {
        return generateNow(recordId, teacherId, true);
    }

    @Override
    public EduHomeworkAiReviewDO getLatestByRecordId(Long homeworkRecordId) {
        accessService.requireRecordViewAccess(homeworkRecordId);
        return aiReviewMapper.selectLatestByRecordId(homeworkRecordId);
    }

    @Override
    public EduHomeworkAiReviewDO getLatestForCorrection(Long homeworkRecordId) {
        return aiReviewMapper.selectLatestByRecordId(homeworkRecordId);
    }

    @Override
    public void applyLatestReview(Long homeworkRecordId) {
        accessService.requireRecordAccess(homeworkRecordId);
        throw exception(AI_REVIEW_DIRECT_APPLY_DISABLED);
    }

    @Override
    public void rejectLatestReview(Long homeworkRecordId) {
        accessService.requireRecordAccess(homeworkRecordId);
        EduHomeworkAiReviewDO review = aiReviewMapper.selectLatestByRecordId(homeworkRecordId);
        if (review == null) {
            return;
        }
        EduHomeworkAiReviewDO update = new EduHomeworkAiReviewDO();
        update.setId(review.getId());
        update.setStatus(HomeworkAiReviewStatusEnum.REJECTED);
        update.setTeacherDecision("reject");
        aiReviewMapper.update(update, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<EduHomeworkAiReviewDO>()
                .eq(EduHomeworkAiReviewDO::getId, review.getId())
                .in(EduHomeworkAiReviewDO::getStatus, HomeworkAiReviewStatusEnum.QUEUED,
                        HomeworkAiReviewStatusEnum.PROCESSING, HomeworkAiReviewStatusEnum.READY));
    }

    @Override
    public void markTeacherReviewed(Long homeworkRecordId, Integer score, String comment) {
        EduHomeworkAiReviewDO review = aiReviewMapper.selectLatestByRecordId(homeworkRecordId);
        if (review == null || !Arrays.asList(
                        HomeworkAiReviewStatusEnum.READY,
                        HomeworkAiReviewStatusEnum.ACCEPTED,
                        HomeworkAiReviewStatusEnum.REJECTED)
                .contains(review.getStatus())) {
            return;
        }
        EduHomeworkRecordDO record = homeworkRecordMapper.selectById(homeworkRecordId);
        EduHomeworkDO homework = record != null ? homeworkMapper.selectById(record.getHomeworkId()) : null;
        String suggestedComment = studentVisibleCommentFormatter.format(homework, review.getAiScore(), review.getAiComment());
        boolean accepted = review.getAiScore() != null
                && review.getAiScore().equals(score)
                && StrUtil.equals(StrUtil.trim(suggestedComment), StrUtil.trim(comment));
        EduHomeworkAiReviewDO update = new EduHomeworkAiReviewDO();
        update.setId(review.getId());
        update.setStatus(accepted
                ? HomeworkAiReviewStatusEnum.ACCEPTED
                : HomeworkAiReviewStatusEnum.REJECTED);
        update.setTeacherDecision(accepted ? "accept" : "adjust");
        update.setTeacherScore(score);
        update.setTeacherComment(comment);
        aiReviewMapper.updateById(update);
    }

    @Override
    public String buildStudentCommentSuggestion(Long homeworkRecordId) {
        EduHomeworkAiReviewDO review = getLatestByRecordId(homeworkRecordId);
        if (review == null || StrUtil.isBlank(review.getAiComment())) {
            return "";
        }
        EduHomeworkRecordDO record = homeworkRecordMapper.selectById(homeworkRecordId);
        EduHomeworkDO homework = record != null ? homeworkMapper.selectById(record.getHomeworkId()) : null;
        return studentVisibleCommentFormatter.format(homework, review.getAiScore(), review.getAiComment());
    }

    @Override
    public Double getConfidenceThreshold(Long homeworkRecordId) {
        EduHomeworkRecordDO record = homeworkRecordMapper.selectById(homeworkRecordId);
        EduHomeworkDO homework = record != null ? homeworkMapper.selectById(record.getHomeworkId()) : null;
        return homework != null && homework.getAiConfidenceThreshold() != null
                ? homework.getAiConfidenceThreshold() : 0.7D;
    }

    /**
     * 教师发起的即时生成（单份同步、批量任务逐份）：不经排队，直接写入「处理中」并调用模型。
     *
     * @param requireOwner true 时校验 teacherId 是任务的布置教师
     */
    private EduHomeworkAiReviewDO generateNow(Long homeworkRecordId, Long teacherId, boolean requireOwner) {
        EduHomeworkRecordDO record = homeworkRecordMapper.selectById(homeworkRecordId);
        if (record == null) {
            throw exception(AI_REVIEW_RECORD_NOT_EXISTS);
        }
        EduHomeworkDO homework = homeworkMapper.selectById(record.getHomeworkId());
        if (homework == null) {
            throw exception(AI_REVIEW_RECORD_NOT_EXISTS);
        }
        if (requireOwner && !Objects.equals(homework.getTeacherId(), teacherId)) {
            throw exception(AI_REVIEW_STATUS_INVALID);
        }
        if (!EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(record.getStatus())) {
            throw exception(AI_REVIEW_STATUS_INVALID);
        }
        EduHomeworkAiReviewDO review = prepareDraftRecord(homework, record, HomeworkAiReviewStatusEnum.PROCESSING);
        aiReviewMapper.insert(review);
        return runReview(homework, record, review, teacherId != null ? teacherId : homework.getTeacherId(), false);
    }

    /**
     * 对一条已处于「处理中」的初评抽取证据并调用模型。任何异常都会把记录写成「失败」并注明原因，然后抛出。
     *
     * @param notifyOnFailure 是否通知布置教师（自动排队的初评需要；教师当面发起的不需要）
     */
    private EduHomeworkAiReviewDO runReview(EduHomeworkDO homework, EduHomeworkRecordDO record,
                                            EduHomeworkAiReviewDO review, Long calibrationTeacherId,
                                            boolean notifyOnFailure) {
        ReviewEvidence evidence;
        try {
            evidence = reviewEvidenceService.build(homework, record);
        } catch (RuntimeException ex) {
            String detail = HomeworkAiReviewFailures.truncate(
                    StrUtil.blankToDefault(StrUtil.trim(ex.getMessage()), ex.getClass().getSimpleName()), 120);
            markFailed(review, "提交的附件没能读取（" + detail + "）", notifyOnFailure);
            throw ex;
        }
        try {
            if (!evidence.getVideoUrls().isEmpty()) {
                EduHomeworkAiReviewDO skip = HomeworkAiReviewFailures.skipped(review.getId(),
                        HomeworkAiReviewFailures.VIDEO_SKIPPED);
                skip.setEvidenceJson(JSONUtil.toJsonStr(evidence.getPayload()));
                aiReviewMapper.updateIfProcessing(skip, review.getId(), review.getGenerationToken());
                return aiReviewMapper.selectById(review.getId());
            }

            AiEduReviewReqDTO req = new AiEduReviewReqDTO();
            req.setBusinessObjectId(String.valueOf(record.getId()));
            req.setContent(record.getContent());
            req.setSubmissionMode(homework.getSubmissionMode());
            req.setRequirement(homework.getRequirement());
            req.setIndicatorName(resolveIndicatorName(homework));
            req.setPassScore(metricPolicy.passScore(homework));
            req.setAttachmentUrls(record.getAttachmentUrls());
            req.setImageUrlsJson(JSONUtil.toJsonStr(evidence.getImageUrls()));
            req.setTranscript(evidence.getTranscript());
            req.setEvidenceJson(JSONUtil.toJsonStr(evidence.getPayload()));
            req.setRubricJson(homework.getAiRubricJson());
            req.setReferenceJson(homework.getAiReferenceJson());
            req.setCalibrationHint(calibrationService.calculate(calibrationTeacherId, homework).getHint());

            AiEduReviewRespDTO resp = aiEduReviewApi.correctHomework(req);
            EduHomeworkAiReviewDO update = new EduHomeworkAiReviewDO();
            update.setId(review.getId());
            update.setStatus(HomeworkAiReviewStatusEnum.READY);
            update.setModelId(resp.getModelId());
            update.setModelName(resp.getModelName());
            update.setTranscript(evidence.getTranscript());
            update.setEvidenceJson(JSONUtil.toJsonStr(evidence.getPayload()));
            update.setAiScore(resp.getScore());
            update.setAiComment(resp.getComment());
            update.setAiConfidence(resp.getConfidence());
            update.setSubScoresJson(resp.getSubScoresJson());
            update.setRiskFlagsJson(mergeRiskFlags(evidence, resp, homework));
            update.setRawResponse(resp.getRawResponse());
            int updated = aiReviewMapper.updateIfProcessing(update, review.getId(), review.getGenerationToken());
            if (updated == 0) {
                log.info("[runReview] stale result ignored, reviewId={}", review.getId());
            }
            return aiReviewMapper.selectById(review.getId());
        } catch (RuntimeException ex) {
            markFailed(review, HomeworkAiReviewFailures.modelFailureCause(ex), notifyOnFailure);
            throw ex;
        }
    }

    private void markFailed(EduHomeworkAiReviewDO review, String cause, boolean notify) {
        int updated = aiReviewMapper.updateIfProcessing(HomeworkAiReviewFailures.failed(review.getId(), cause),
                review.getId(), review.getGenerationToken());
        if (notify && updated == 1) {
            failureNotifier.notifyFailure(review.getHomeworkId(), cause);
        }
    }

    /** 认领后放弃执行：写成失败或跳过（均不通知，原因已在记录上）。 */
    private EduHomeworkAiReviewDO finish(EduHomeworkAiReviewDO review, EduHomeworkAiReviewDO outcome) {
        aiReviewMapper.updateIfProcessing(outcome, review.getId(), review.getGenerationToken());
        return aiReviewMapper.selectById(review.getId());
    }

    /** 任务开启了 AI 初评。早期 autoGrade=1、aiReviewMode=0 的任务同样视为开启（V18 已归一化）。 */
    static boolean isAutoPreReview(EduHomeworkDO homework) {
        return homework != null && Objects.equals(homework.getAutoGrade(), 1);
    }

    /** 初评对应的是这份提交的当前版本（重交后提交时间会变）。 */
    private static boolean isCurrent(EduHomeworkAiReviewDO review, EduHomeworkRecordDO record) {
        return review != null && Objects.equals(review.getSourceSubmitTime(), record.getSubmitTime());
    }

    private EduHomeworkAiReviewDO prepareDraftRecord(EduHomeworkDO homework, EduHomeworkRecordDO record, int status) {
        EduHomeworkAiReviewDO review = new EduHomeworkAiReviewDO();
        review.setHomeworkRecordId(record.getId());
        review.setHomeworkId(record.getHomeworkId());
        review.setStudentId(record.getStudentId());
        review.setStatus(status);
        review.setSubmissionMode(StrUtil.blankToDefault(homework.getSubmissionMode(), "text"));
        review.setGenerationToken(UUID.randomUUID().toString());
        review.setSourceSubmitTime(record.getSubmitTime());
        return review;
    }

    private String mergeRiskFlags(ReviewEvidence evidence, AiEduReviewRespDTO response, EduHomeworkDO homework) {
        Set<String> flags = new LinkedHashSet<>(evidence.getRiskFlags());
        if (StrUtil.isNotBlank(response.getRiskFlagsJson())) {
            try {
                flags.addAll(JSONUtil.toList(JSONUtil.parseArray(response.getRiskFlagsJson()), String.class));
            } catch (Exception ex) {
                flags.add("invalid_model_risk_flags");
            }
        }
        double threshold = homework.getAiConfidenceThreshold() != null
                ? homework.getAiConfidenceThreshold() : 0.7D;
        if (response.getConfidence() == null || response.getConfidence() < threshold) {
            flags.add("low_confidence");
            flags.add("manual_review_required");
        }
        return JSONUtil.toJsonStr(flags);
    }

    /**
     * 解析任务关联的技能指标名称：有二级指标时为“一级 / 二级”，仅一级时为一级名称，都没有时返回 null，由 AI 侧兜底。
     */
    private String resolveIndicatorName(EduHomeworkDO homework) {
        CapacityIndicatorRespDTO secondIndicator = homework.getIndicatorSecondId() != null
                ? capacityIndicatorApi.getIndicator(homework.getIndicatorSecondId()) : null;
        Long firstIndicatorId = homework.getIndicatorFirstId() != null ? homework.getIndicatorFirstId()
                : secondIndicator != null ? secondIndicator.getParentIndicatorId() : null;
        CapacityIndicatorRespDTO firstIndicator = firstIndicatorId != null
                ? capacityIndicatorApi.getIndicator(firstIndicatorId) : null;
        String firstName = firstIndicator != null ? StrUtil.trimToNull(firstIndicator.getIndicatorName()) : null;
        String secondName = secondIndicator != null ? StrUtil.trimToNull(secondIndicator.getIndicatorName()) : null;
        if (secondName != null) {
            return firstName != null ? firstName + " / " + secondName : secondName;
        }
        return firstName;
    }
}
