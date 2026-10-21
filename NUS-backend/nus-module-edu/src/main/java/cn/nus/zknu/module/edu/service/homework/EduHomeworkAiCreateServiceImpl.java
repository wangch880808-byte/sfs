package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateConfirmReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateConfirmRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateRegenerateReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateTaskStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAiCreatePreviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAiCreatePreviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkAiCreatePreviewSupport.AiCreatePreviewItem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/** AI 创建任务的预览、确认及重启恢复。 */
@Service
@Slf4j
public class EduHomeworkAiCreateServiceImpl implements EduHomeworkAiCreateService {

    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private CapacityIndicatorApi capacityIndicatorApi;
    @Resource
    private EduClassCourseMapper eduClassCourseMapper;
    @Resource
    private EduClassMapper eduClassMapper;
    @Resource
    private EduHomeworkMapper eduHomeworkMapper;
    @Resource
    private EduQuestionBankMapper eduQuestionBankMapper;
    @Resource
    private EduHomeworkAiCreatePreviewMapper eduHomeworkAiCreatePreviewMapper;
    @Resource
    private EduAiBusinessAccessService aiBusinessAccessService;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduHomeworkCreationSupport creationSupport;
    @Resource
    private EduHomeworkPublishService publishService;
    @Resource
    private EduHomeworkAiCreatePreviewSupport previewSupport;
    @Resource
    private EduHomeworkAiCreateRegenerateService regenerateService;
    @Resource(name = "eduAiReviewTaskExecutor")
    private Executor aiTaskExecutor;

    private static final String AI_CREATE_PENDING = "PENDING";
    private static final String AI_CREATE_RUNNING = "RUNNING";
    private static final String AI_CREATE_SUCCEEDED = "SUCCEEDED";
    private static final String AI_CREATE_FAILED = "FAILED";

    @Override
    public Long startAiCreateHomeworkPreviewTask(EduHomeworkAiCreatePreviewReqVO reqVO) {
        Long teacherId = getLoginUserId();
        EduHomeworkAiCreatePreviewDO existing = eduHomeworkAiCreatePreviewMapper
                .selectByTeacherAndClientRequestId(teacherId, reqVO.getClientRequestId());
        if (existing != null) {
            return existing.getId();
        }
        AiCreateContext context = validateAiCreateContext(reqVO);
        List<EduQuestionBankDO> sampledQuestions = sampleQuestions(context.candidates(), reqVO.getQuestionCount());
        String previewId = UUID.randomUUID().toString();
        EduHomeworkAiCreatePreviewDO preview = new EduHomeworkAiCreatePreviewDO();
        preview.setClientRequestId(reqVO.getClientRequestId());
        preview.setTaskStatus(AI_CREATE_PENDING);
        preview.setProgress(0);
        preview.setRetryCount(0);
        preview.setPreviewId(previewId);
        preview.setTeacherId(context.teacherId());
        preview.setCourseId(reqVO.getCourseId());
        preview.setIndicatorFirstId(context.secondIndicator().getParentIndicatorId());
        preview.setIndicatorSecondId(reqVO.getIndicatorSecondId());
        preview.setClassIdsJson(JSONUtil.toJsonStr(context.classIds()));
        preview.setQuestionIdsJson(JSONUtil.toJsonStr(sampledQuestions.stream().map(EduQuestionBankDO::getId).toList()));
        preview.setItemsJson("[]");
        preview.setHomeworkType(reqVO.getHomeworkType());
        preview.setHomeworkLevel(reqVO.getHomeworkLevel());
        preview.setDeadline(reqVO.getDeadline());
        preview.setAutoGrade(reqVO.getAutoGrade());
        preview.setSubmissionMode(reqVO.getSubmissionMode());
        preview.setAiReviewMode(HomeworkGradingMode.normalizeAiReviewMode(
                reqVO.getAutoGrade(), reqVO.getAiReviewMode()));
        preview.setPassScore(reqVO.getPassScore());
        preview.setScore(reqVO.getScore());
        preview.setExpireTime(LocalDateTime.now().plusHours(2));
        preview.setConfirmed(false);
        try {
            eduHomeworkAiCreatePreviewMapper.insert(preview);
        } catch (DuplicateKeyException ex) {
            EduHomeworkAiCreatePreviewDO duplicate = eduHomeworkAiCreatePreviewMapper
                    .selectByTeacherAndClientRequestId(context.teacherId(), reqVO.getClientRequestId());
            if (duplicate != null) {
                return duplicate.getId();
            }
            throw ex;
        }
        submitAiCreateTask(preview.getId());
        return preview.getId();
    }

    @Override
    public EduHomeworkAiCreateTaskStatusRespVO getAiCreateHomeworkPreviewTaskStatus(Long taskId) {
        EduHomeworkAiCreatePreviewDO task = eduHomeworkAiCreatePreviewMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTeacherId(), getLoginUserId())) {
            throw exception(HOMEWORK_AI_CREATE_TASK_NOT_EXISTS);
        }
        EduHomeworkAiCreateTaskStatusRespVO resp = new EduHomeworkAiCreateTaskStatusRespVO();
        resp.setTaskId(task.getId());
        resp.setStatus(task.getTaskStatus());
        resp.setProgress(task.getProgress());
        resp.setErrorMessage(task.getErrorMessage());
        if (AI_CREATE_SUCCEEDED.equals(task.getTaskStatus())) {
            List<AiCreatePreviewItem> items = previewSupport.parsePreviewItems(task.getItemsJson());
            resp.setPreview(previewSupport.buildPreviewResp(task.getPreviewId(),
                    JSONUtil.toList(JSONUtil.parseArray(task.getClassIdsJson()), Long.class).size(), items));
        }
        return resp;
    }

    @Override
    public void recoverAiCreateHomeworkPreviewTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(30);
        eduHomeworkAiCreatePreviewMapper.update(new EduHomeworkAiCreatePreviewDO(),
                new LambdaUpdateWrapper<EduHomeworkAiCreatePreviewDO>()
                        .set(EduHomeworkAiCreatePreviewDO::getTaskStatus, AI_CREATE_PENDING)
                        .set(EduHomeworkAiCreatePreviewDO::getProgress, 0)
                        .set(EduHomeworkAiCreatePreviewDO::getStartTime, null)
                        .setSql("retry_count = retry_count + 1")
                        .eq(EduHomeworkAiCreatePreviewDO::getTaskStatus, AI_CREATE_RUNNING)
                        .lt(EduHomeworkAiCreatePreviewDO::getUpdateTime, staleBefore));
        eduHomeworkAiCreatePreviewMapper.selectPendingTasks(100)
                .forEach(task -> submitAiCreateTask(task.getId()));
    }

    private void submitAiCreateTask(Long taskId) {
        int claimed = eduHomeworkAiCreatePreviewMapper.update(new EduHomeworkAiCreatePreviewDO(),
                new LambdaUpdateWrapper<EduHomeworkAiCreatePreviewDO>()
                        .set(EduHomeworkAiCreatePreviewDO::getTaskStatus, AI_CREATE_RUNNING)
                        .set(EduHomeworkAiCreatePreviewDO::getStartTime, LocalDateTime.now())
                        .set(EduHomeworkAiCreatePreviewDO::getErrorMessage, null)
                        .eq(EduHomeworkAiCreatePreviewDO::getId, taskId)
                        .eq(EduHomeworkAiCreatePreviewDO::getTaskStatus, AI_CREATE_PENDING));
        if (claimed == 0) {
            return;
        }
        try {
            aiTaskExecutor.execute(() -> processAiCreateTask(taskId));
        } catch (org.springframework.core.task.TaskRejectedException ex) {
            eduHomeworkAiCreatePreviewMapper.update(new EduHomeworkAiCreatePreviewDO(),
                    new LambdaUpdateWrapper<EduHomeworkAiCreatePreviewDO>()
                            .set(EduHomeworkAiCreatePreviewDO::getTaskStatus, AI_CREATE_PENDING)
                            .set(EduHomeworkAiCreatePreviewDO::getStartTime, null)
                            .eq(EduHomeworkAiCreatePreviewDO::getId, taskId)
                            .eq(EduHomeworkAiCreatePreviewDO::getTaskStatus, AI_CREATE_RUNNING));
            log.warn("[submitAiCreateTask] queue is full, task returned to pending, taskId={}", taskId);
        }
    }

    private void processAiCreateTask(Long taskId) {
        try {
            EduHomeworkAiCreatePreviewDO task = eduHomeworkAiCreatePreviewMapper.selectById(taskId);
            CapacityCourseRespDTO course = capacityCourseApi.getCourse(task.getCourseId());
            CapacityIndicatorRespDTO indicator = capacityIndicatorApi.getIndicator(task.getIndicatorSecondId());
            List<Long> questionIds = JSONUtil.toList(JSONUtil.parseArray(task.getQuestionIdsJson()), Long.class);
            Map<Long, EduQuestionBankDO> questionMap = eduQuestionBankMapper.selectBatchIds(questionIds).stream()
                    .collect(Collectors.toMap(EduQuestionBankDO::getId, question -> question));
            AiCreateContext context = new AiCreateContext(course, indicator,
                    JSONUtil.toList(JSONUtil.parseArray(task.getClassIdsJson()), Long.class), Map.of(),
                    task.getTeacherId(), task.getHomeworkLevel(), List.of());
            List<AiCreatePreviewItem> items = new ArrayList<>();
            for (Long questionId : questionIds) {
                EduQuestionBankDO question = questionMap.get(questionId);
                if (question == null) {
                    throw new IllegalStateException("题库题目不存在：" + questionId);
                }
                try {
                    items.add(buildPreviewItem(context, question));
                } catch (Exception ex) {
                    logAiCreateFailure(taskId, questionId, ex);
                    items.add(buildFailedPreviewItem(question, ex));
                }
                int progress = questionIds.isEmpty() ? 100 : items.size() * 100 / questionIds.size();
                updateAiCreateTaskProgress(taskId, items, progress);
            }
            EduHomeworkAiCreatePreviewDO update = new EduHomeworkAiCreatePreviewDO();
            update.setId(taskId);
            update.setTaskStatus(AI_CREATE_SUCCEEDED);
            update.setProgress(100);
            update.setItemsJson(JSONUtil.toJsonStr(items));
            update.setFinishTime(LocalDateTime.now());
            eduHomeworkAiCreatePreviewMapper.updateById(update);
        } catch (Exception ex) {
            logAiCreateFailure(taskId, null, ex);
            markAiCreateTaskFailed(taskId, StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "AI 模型调用失败"), 1000));
        }
    }

    private AiCreatePreviewItem buildFailedPreviewItem(EduQuestionBankDO question, Exception ex) {
        AiCreatePreviewItem item = new AiCreatePreviewItem();
        item.setPreviewItemId(UUID.randomUUID().toString());
        item.setSourceQuestionId(question.getId());
        item.setSourceTitle(question.getTitle());
        item.setSourceRequirement(question.getRequirement());
        item.setSuccess(false);
        item.setErrorMessage(StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "AI 模型调用失败"), 1000));
        return item;
    }

    private void updateAiCreateTaskProgress(Long taskId, List<AiCreatePreviewItem> items, int progress) {
        EduHomeworkAiCreatePreviewDO update = new EduHomeworkAiCreatePreviewDO();
        update.setId(taskId);
        update.setItemsJson(JSONUtil.toJsonStr(items));
        update.setProgress(progress);
        eduHomeworkAiCreatePreviewMapper.updateById(update);
    }

    private void markAiCreateTaskFailed(Long taskId, String message) {
        EduHomeworkAiCreatePreviewDO update = new EduHomeworkAiCreatePreviewDO();
        update.setId(taskId);
        update.setTaskStatus(AI_CREATE_FAILED);
        update.setErrorMessage(message);
        update.setFinishTime(LocalDateTime.now());
        eduHomeworkAiCreatePreviewMapper.updateById(update);
    }

    private void logAiCreateFailure(Long taskId, Long questionId, Exception ex) {
        log.error("[processAiCreateTask] failed, taskId={}, questionId={}", taskId, questionId, ex);
    }

    // 确认与任务发布沿用 EduHomeworkServiceImpl 的事务边界。
    @Override
    public EduHomeworkAiCreateConfirmRespVO confirmAiCreateHomework(EduHomeworkAiCreateConfirmReqVO reqVO) {
        EduHomeworkAiCreatePreviewDO preview = previewSupport.validatePreviewSession(reqVO.getPreviewId(), false);
        List<AiCreatePreviewItem> items = previewSupport.parsePreviewItems(preview.getItemsJson());
        Map<String, AiCreatePreviewItem> itemMap = items.stream()
                .collect(Collectors.toMap(AiCreatePreviewItem::getPreviewItemId, item -> item, (left, right) -> left));
        if (itemMap.size() != reqVO.getItems().size()) {
            throw exception(HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID);
        }
        Set<String> confirmedItemIds = new HashSet<>();
        for (EduHomeworkAiCreateConfirmReqVO.Item confirmItem : reqVO.getItems()) {
            if (!confirmedItemIds.add(confirmItem.getPreviewItemId())) {
                throw exception(HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID);
            }
            AiCreatePreviewItem item = itemMap.get(confirmItem.getPreviewItemId());
            if (item == null || StrUtil.isBlank(confirmItem.getHomeworkName()) || StrUtil.isBlank(confirmItem.getRequirement())) {
                throw exception(HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID);
            }
            item.setHomeworkName(confirmItem.getHomeworkName().trim());
            item.setRequirement(confirmItem.getRequirement().trim());
        }
        if (items.stream().anyMatch(item -> StrUtil.isBlank(item.getHomeworkName()) || StrUtil.isBlank(item.getRequirement()))) {
            throw exception(HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID);
        }
        int updated = eduHomeworkAiCreatePreviewMapper.update(new EduHomeworkAiCreatePreviewDO(),
                new LambdaUpdateWrapper<EduHomeworkAiCreatePreviewDO>()
                        .set(EduHomeworkAiCreatePreviewDO::getConfirmed, true)
                        .set(EduHomeworkAiCreatePreviewDO::getItemsJson, JSONUtil.toJsonStr(items))
                        .eq(EduHomeworkAiCreatePreviewDO::getId, preview.getId())
                        .eq(EduHomeworkAiCreatePreviewDO::getConfirmed, false));
        if (updated == 0) {
            throw exception(HOMEWORK_AI_CREATE_PREVIEW_CONFIRMED);
        }

        List<Long> classIds = JSONUtil.toList(JSONUtil.parseArray(preview.getClassIdsJson()), Long.class);
        Map<Long, EduClassCourseDO> relationMap = validateClassRelations(preview.getCourseId(), classIds);
        int createdCount = 0;
        for (Long classId : classIds) {
            EduClassCourseDO relation = relationMap.get(classId);
            for (AiCreatePreviewItem item : items) {
                EduHomeworkDO homework = new EduHomeworkDO();
                homework.setHomeworkName(creationSupport.generateUniqueName(classId, preview.getCourseId(), item.getHomeworkName()));
                homework.setHomeworkType(preview.getHomeworkType());
                homework.setHomeworkLevel(preview.getHomeworkLevel());
                homework.setCourseId(preview.getCourseId());
                homework.setClassId(classId);
                homework.setSemesterName(relation.getSemesterName());
                homework.setIndicatorFirstId(preview.getIndicatorFirstId());
                homework.setIndicatorSecondId(preview.getIndicatorSecondId());
                homework.setRequirement(item.getRequirement());
                homework.setDeadline(preview.getDeadline());
                homework.setAutoGrade(preview.getAutoGrade());
                homework.setSubmissionMode(preview.getSubmissionMode());
                homework.setAiReviewMode(preview.getAiReviewMode());
                homework.setRequiredAttachmentTypesJson(creationSupport.buildRequiredAttachmentTypesJson(preview.getSubmissionMode()));
                homework.setPassScore(preview.getPassScore());
                homework.setScore(preview.getScore());
                homework.setTeacherId(preview.getTeacherId());
                creationSupport.fillHomeworkDefaults(homework);
                eduHomeworkMapper.insert(homework);
                publishService.snapshotAssigneesIfPublished(homework);
                publishService.notifyPublishedHomework(homework, EduBusinessNotifyService.HOMEWORK_CREATED);
                createdCount++;
            }
        }

        EduHomeworkAiCreateConfirmRespVO respVO = new EduHomeworkAiCreateConfirmRespVO();
        respVO.setQuestionCount(items.size());
        respVO.setClassCount(classIds.size());
        respVO.setCreatedCount(createdCount);
        return respVO;
    }

    private AiCreateContext validateAiCreateContext(EduHomeworkAiCreatePreviewReqVO reqVO) {
        creationSupport.validateScoreConfig(reqVO.getPassScore(), reqVO.getScore());
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(reqVO.getCourseId());
        if (course == null) {
            throw exception(COURSE_NOT_EXISTS);
        }
        if (Objects.equals(course.getStatus(), 1)) {
            throw exception(COURSE_ARCHIVED);
        }
        CapacityIndicatorRespDTO secondIndicator = capacityIndicatorApi.getIndicator(reqVO.getIndicatorSecondId());
        if (secondIndicator == null || secondIndicator.getIndicatorLevel() == null
                || secondIndicator.getIndicatorLevel() != 2 || secondIndicator.getParentIndicatorId() == null) {
            throw exception(QUESTION_BANK_INDICATOR_MISMATCH);
        }
        if (course.getIndicatorId() != null && !Objects.equals(course.getIndicatorId(), secondIndicator.getParentIndicatorId())) {
            throw exception(QUESTION_BANK_INDICATOR_MISMATCH);
        }
        List<Long> classIds = reqVO.getClassIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            throw exception(CLASS_COURSE_NOT_EXISTS);
        }
        Map<Long, EduClassCourseDO> relationMap = validateClassRelations(reqVO.getCourseId(), classIds);
        Long teacherId = getLoginUserId();
        List<EduQuestionBankDO> candidates = eduQuestionBankMapper.selectList(
                new LambdaQueryWrapper<EduQuestionBankDO>()
                        .eq(EduQuestionBankDO::getTeacherId, teacherId)
                        .eq(EduQuestionBankDO::getIndicatorFirstId, secondIndicator.getParentIndicatorId())
                        .eq(EduQuestionBankDO::getIndicatorSecondId, reqVO.getIndicatorSecondId())
                        .eq(EduQuestionBankDO::getHomeworkLevel, reqVO.getHomeworkLevel()));
        if (candidates.size() < reqVO.getQuestionCount()) {
            throw exception(QUESTION_BANK_NOT_ENOUGH, candidates.size());
        }
        return new AiCreateContext(course, secondIndicator, classIds, relationMap, teacherId, reqVO.getHomeworkLevel(), candidates);
    }

    private Map<Long, EduClassCourseDO> validateClassRelations(Long courseId, List<Long> classIds) {
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(courseId);
        if (course != null && Objects.equals(course.getStatus(), 1)) {
            throw exception(COURSE_ARCHIVED);
        }
        aiBusinessAccessService.validateCourseAccess(courseId);
        if (dataScopeService.isPublicCourse(courseId)) {
            for (Long classId : classIds) {
                if (!dataScopeService.canAssignToPublicClass(courseId, classId)) {
                    throw exception(FORBIDDEN);
                }
            }
        } else {
            classIds.forEach(aiBusinessAccessService::validateClassAccess);
        }
        List<EduClassCourseDO> relations = eduClassCourseMapper.selectList(
                new LambdaQueryWrapper<EduClassCourseDO>()
                        .eq(EduClassCourseDO::getCourseId, courseId)
                        .eq(EduClassCourseDO::getStatus, 0)
                        .in(EduClassCourseDO::getClassId, classIds)
                        .orderByDesc(EduClassCourseDO::getId));
        Map<Long, EduClassCourseDO> relationMap = relations.stream()
                .collect(Collectors.toMap(EduClassCourseDO::getClassId, relation -> relation, (left, right) -> left));
        if (relationMap.size() != classIds.size()) {
            throw exception(CLASS_COURSE_ARCHIVED);
        }
        for (Long classId : classIds) {
            EduClassDO clazz = eduClassMapper.selectById(classId);
            if (clazz == null || Objects.equals(clazz.getStatus(), 1)) {
                throw exception(CLASS_ARCHIVED);
            }
        }
        return relationMap;
    }

    private List<EduQuestionBankDO> sampleQuestions(List<EduQuestionBankDO> candidates, Integer questionCount) {
        List<EduQuestionBankDO> sampledQuestions = new ArrayList<>(candidates);
        Collections.shuffle(sampledQuestions);
        return sampledQuestions.subList(0, questionCount);
    }

    private AiCreatePreviewItem buildPreviewItem(AiCreateContext context, EduQuestionBankDO question) {
        AiCreatePreviewItem item = new AiCreatePreviewItem();
        item.setPreviewItemId(UUID.randomUUID().toString());
        item.setSourceQuestionId(question.getId());
        item.setSourceTitle(question.getTitle());
        item.setSourceRequirement(question.getRequirement());
        previewSupport.applyRewriteResult(item, previewSupport.rewriteQuestion(context.course(), context.secondIndicator(), context.homeworkLevel(), question));
        return item;
    }

    @Override
    public EduHomeworkAiCreatePreviewRespVO.Item regenerateAiCreateHomeworkQuestion(EduHomeworkAiCreateRegenerateReqVO reqVO) {
        return regenerateService.regenerateAiCreateHomeworkQuestion(reqVO);
    }

    private record AiCreateContext(CapacityCourseRespDTO course,
                                   CapacityIndicatorRespDTO secondIndicator,
                                   List<Long> classIds,
                                   Map<Long, EduClassCourseDO> relationMap,
                                   Long teacherId,
                                   Integer homeworkLevel,
                                   List<EduQuestionBankDO> candidates) {
    }


}
