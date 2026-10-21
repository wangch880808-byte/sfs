package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordCorrectReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordGradeRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordUpdateReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordHistoryRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkGradingActionDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordPageMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.*;

/**
 * 任务提交记录 Service 实现类
 */
@Service
public class EduHomeworkRecordServiceImpl implements EduHomeworkRecordService {

    @Resource
    private EduHomeworkRecordMapper eduHomeworkRecordMapper;
    @Resource
    private EduHomeworkMapper eduHomeworkMapper;
    @Resource
    private EduHomeworkAiReviewService eduHomeworkAiReviewService;
    @Resource
    private EduHomeworkRecordHistoryService historyService;
    @Resource
    private EduHomeworkAssigneeMapper eduHomeworkAssigneeMapper;
    @Resource
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Resource
    private HomeworkAttachmentService homeworkAttachmentService;
    @Resource
    private EduHomeworkExtensionService homeworkExtensionService;
    @Resource
    private EduHomeworkGradingActionService gradingActionService;
    @Resource
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Resource
    private AdminUserService adminUserService;

    @Resource
    private EduHomeworkRecordPageMapper recordPageMapper;
    @Resource
    private EduAiBusinessAccessService businessAccessService;

    /** 批改队列一次查询 AI 初评状态的记录数上限，超过时分批查询。 */
    private static final int AI_STATUS_QUERY_CHUNK = 500;
    /** 打回允许的原状态（已打回时只调整重做期限）。 */
    private static final List<Integer> REDO_ALLOWED = List.of(
            EduHomeworkRecordStatusEnum.SUBMITTED, EduHomeworkRecordStatusEnum.CORRECTED,
            EduHomeworkRecordStatusEnum.REDO, EduHomeworkRecordStatusEnum.RESUBMITTED);
    /** 打回会改变状态的原状态。 */
    private static final List<Integer> REDO_FROM = List.of(
            EduHomeworkRecordStatusEnum.SUBMITTED, EduHomeworkRecordStatusEnum.CORRECTED,
            EduHomeworkRecordStatusEnum.RESUBMITTED);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitRecord(EduHomeworkRecordSubmitReqVO submitReqVO, Long studentId) {
        // 校验任务存在
        EduHomeworkDO homework = validateHomeworkExists(submitReqVO.getHomeworkId());
        if (!archiveAccessService.canSubmit(homework)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        validateAssignee(submitReqVO.getHomeworkId(), studentId);
        // 截止时间校验：有个别延期时以任务截止时间与个人截止时间中较晚的为准
        validateDeadline(homework, studentId);
        // 重复提交检查
        validateNotDuplicate(submitReqVO.getHomeworkId(), studentId);

        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setHomeworkId(submitReqVO.getHomeworkId());
        record.setStudentId(studentId);
        record.setContent(submitReqVO.getContent());
        record.setAttachmentUrls(homeworkAttachmentService.validateAndNormalize(homework, studentId,
                submitReqVO.getContent(), submitReqVO.getAttachmentUrls()));
        record.setSubmitTime(LocalDateTime.now());
        record.setUpdateTime(nextUpdateTime(record));
        record.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED); // 已提交待批改
        record.setRedoCount(0);
        try {
            eduHomeworkRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // 同一学生并发提交：唯一键挡住后到的一份，按“已提交”提示，而不是系统异常
            throw exception(HOMEWORK_RECORD_ALREADY_SUBMITTED);
        }
        historyService.append(record, "SUBMIT", 1, null, null, null, null);
        if (homework.getAutoGrade() != null && homework.getAutoGrade() == 1) {
            eduHomeworkAiReviewService.enqueuePreReview(record.getId());
        }
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(EduHomeworkRecordUpdateReqVO updateReqVO, Long studentId) {
        EduHomeworkRecordDO record = validateRecordExists(updateReqVO.getId());
        if (!Objects.equals(record.getStudentId(), studentId)) {
            throw exception(HOMEWORK_RECORD_NOT_EXISTS);
        }
        if (!Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.REDO)) {
            throw exception(HOMEWORK_RECORD_UPDATE_FORBIDDEN);
        }
        // 截止时间校验：打回重做的记录以重做截止时间为准
        EduHomeworkDO homework = validateHomeworkExists(record.getHomeworkId());
        if (!archiveAccessService.canSubmit(homework)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        if (HomeworkDeadlineUtils.isExpired(HomeworkDeadlineUtils.getEffectiveDeadline(homework, record))) {
            throw exception(HOMEWORK_RECORD_DEADLINE_EXCEEDED);
        }

        EduHomeworkRecordDO updateObj = new EduHomeworkRecordDO();
        updateObj.setContent(updateReqVO.getContent());
        updateObj.setAttachmentUrls(homeworkAttachmentService.validateAndNormalize(homework, studentId,
                updateReqVO.getContent(), updateReqVO.getAttachmentUrls()));
        updateObj.setSubmitTime(LocalDateTime.now());
        updateObj.setUpdateTime(nextUpdateTime(record));
        updateObj.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED); // 已重新提交
        // 只从“已打回”改为“已重新提交”：重复提交或教师同时撤销打回时，后到的一方失败
        int updated = eduHomeworkRecordMapper.update(updateObj, new LambdaUpdateWrapper<EduHomeworkRecordDO>()
                .eq(EduHomeworkRecordDO::getId, record.getId())
                .eq(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.REDO));
        if (updated == 0) {
            throw exception(HOMEWORK_RECORD_STATE_CHANGED);
        }
        historyService.append(record, "RESUBMIT", 4, record.getScore(), record.getComment(),
                record.getRedoDeadline(), null);
        if (homework.getAutoGrade() != null && homework.getAutoGrade() == 1) {
            eduHomeworkAiReviewService.enqueuePreReview(record.getId());
        }
    }

    @Override
    public EduHomeworkRecordDO getRecord(Long id) {
        EduHomeworkRecordDO record = eduHomeworkRecordMapper.selectById(id);
        if (record == null) {
            throw exception(HOMEWORK_RECORD_NOT_EXISTS);
        }
        businessAccessService.requireHomeworkViewAccess(record.getHomeworkId());
        return record;
    }

    @Override
    public List<EduHomeworkRecordHistoryRespVO> getHistory(Long recordId) {
        getRecord(recordId);
        return historyService.listByRecordId(recordId);
    }

    @Override
    public List<EduHomeworkRecordHistoryRespVO> getStudentHistory(Long homeworkId, Long studentId) {
        EduHomeworkDO homework = validateHomeworkExists(homeworkId);
        EduHomeworkRecordDO record = eduHomeworkRecordMapper.selectOne(new LambdaQueryWrapper<EduHomeworkRecordDO>()
                .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId)
                .eq(EduHomeworkRecordDO::getStudentId, studentId));
        // 只读轨迹按“历史可见”判断：班级、课程或班课归档后学生仍能查看自己的提交与成绩
        if (!archiveAccessService.canViewHistory(homework, record != null)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        validateAssignee(homeworkId, studentId);
        return record == null ? Collections.emptyList() : historyService.listByRecordId(record.getId());
    }

    @Override
    public PageResult<EduHomeworkRecordDO> getRecordPage(EduHomeworkRecordPageReqVO reqVO) {
        if (reqVO.getHomeworkId() == null) {
            Long teacherId = getLoginUserId();
            return teacherId == null ? PageResult.empty() : recordPageMapper.selectOwnedPage(reqVO, teacherId, false);
        }
        LambdaQueryWrapperX<EduHomeworkRecordDO> queryWrapper = new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                .eqIfPresent(EduHomeworkRecordDO::getHomeworkId, reqVO.getHomeworkId())
                .eqIfPresent(EduHomeworkRecordDO::getStudentId, reqVO.getStudentId())
                .eqIfPresent(EduHomeworkRecordDO::getStatus, reqVO.getStatus())
                .orderByDesc(EduHomeworkRecordDO::getId);
        businessAccessService.requireHomeworkViewAccess(reqVO.getHomeworkId());
        return eduHomeworkRecordMapper.selectPage(reqVO, queryWrapper);
    }

    @Override
    public PageResult<EduHomeworkRecordDO> getCorrectPage(EduHomeworkRecordPageReqVO reqVO) {
        if (reqVO.getHomeworkId() == null) {
            Long teacherId = getLoginUserId();
            return teacherId == null ? PageResult.empty() : recordPageMapper.selectOwnedPage(reqVO, teacherId, true);
        }
        LambdaQueryWrapperX<EduHomeworkRecordDO> queryWrapper = new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                .eq(EduHomeworkRecordDO::getHomeworkId, reqVO.getHomeworkId());
        validateHomeworkAccess(validateHomeworkExists(reqVO.getHomeworkId()));
        if (reqVO.getStatus() != null) {
            if (reqVO.getStatus() == EduHomeworkRecordStatusEnum.SUBMITTED) {
                queryWrapper.in(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.PENDING_REVIEW); // 待批改 + 已重新提交
            } else {
                queryWrapper.eq(EduHomeworkRecordDO::getStatus, reqVO.getStatus());
            }
        } else {
            queryWrapper.in(EduHomeworkRecordDO::getStatus, Arrays.asList(EduHomeworkRecordStatusEnum.SUBMITTED,
                        EduHomeworkRecordStatusEnum.CORRECTED, EduHomeworkRecordStatusEnum.REDO,
                        EduHomeworkRecordStatusEnum.RESUBMITTED));
        }
        return eduHomeworkRecordMapper.selectPage(reqVO, queryWrapper
                .orderByAsc(EduHomeworkRecordDO::getStatus)
                .orderByAsc(EduHomeworkRecordDO::getSubmitTime)
                .orderByAsc(EduHomeworkRecordDO::getId));
    }

    @Override
    public EduHomeworkRecordQueueRespVO getGradingQueue(Long homeworkId) {
        businessAccessService.requireHomeworkViewAccess(homeworkId);
        // 只取列表需要的列，不带作答内容与评语；不分页，避免学生多时悄悄截断
        List<EduHomeworkRecordDO> records = eduHomeworkRecordMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .select(EduHomeworkRecordDO::getId, EduHomeworkRecordDO::getHomeworkId,
                                EduHomeworkRecordDO::getStudentId, EduHomeworkRecordDO::getStatus,
                                EduHomeworkRecordDO::getScore, EduHomeworkRecordDO::getSubmitTime,
                                EduHomeworkRecordDO::getCorrectTime, EduHomeworkRecordDO::getRedoDeadline,
                                EduHomeworkRecordDO::getRedoCount, EduHomeworkRecordDO::getUpdateTime)
                        .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId)
                        .in(EduHomeworkRecordDO::getStatus, REDO_ALLOWED)
                        .orderByAsc(EduHomeworkRecordDO::getStatus)
                        .orderByAsc(EduHomeworkRecordDO::getSubmitTime)
                        .orderByAsc(EduHomeworkRecordDO::getId));
        if (records.isEmpty()) {
            return new EduHomeworkRecordQueueRespVO(0, Collections.emptyList());
        }
        Set<Long> studentIds = records.stream().map(EduHomeworkRecordDO::getStudentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserDO> users = studentIds.isEmpty() ? Map.of() : adminUserService.getUserMap(studentIds);
        Map<Long, Integer> aiStatus = getLatestAiReviewStatusMap(
                records.stream().map(EduHomeworkRecordDO::getId).toList());
        List<EduHomeworkRecordQueueItemRespVO> list = new ArrayList<>(records.size());
        for (EduHomeworkRecordDO record : records) {
            EduHomeworkRecordQueueItemRespVO item = new EduHomeworkRecordQueueItemRespVO();
            item.setId(record.getId());
            item.setHomeworkId(record.getHomeworkId());
            item.setStudentId(record.getStudentId());
            item.setStatus(record.getStatus());
            item.setScore(record.getScore());
            item.setSubmitTime(record.getSubmitTime());
            item.setCorrectTime(record.getCorrectTime());
            item.setRedoDeadline(record.getRedoDeadline());
            item.setRedoCount(record.getRedoCount());
            item.setUpdateTime(record.getUpdateTime());
            item.setAiReviewStatus(aiStatus.get(record.getId()));
            AdminUserDO user = users.get(record.getStudentId());
            if (user != null) {
                item.setStudentName(user.getNickname());
                item.setStudentNo(user.getUsername());
            }
            list.add(item);
        }
        return new EduHomeworkRecordQueueRespVO(list.size(), list);
    }

    /** 每份提交最新一次 AI 初评的状态；只查 ID 与状态两列。 */
    private Map<Long, Integer> getLatestAiReviewStatusMap(List<Long> recordIds) {
        Map<Long, Long> latestId = new HashMap<>();
        Map<Long, Integer> result = new HashMap<>();
        for (int from = 0; from < recordIds.size(); from += AI_STATUS_QUERY_CHUNK) {
            List<Long> chunk = recordIds.subList(from, Math.min(recordIds.size(), from + AI_STATUS_QUERY_CHUNK));
            List<EduHomeworkAiReviewDO> reviews = aiReviewMapper.selectList(
                    new LambdaQueryWrapper<EduHomeworkAiReviewDO>()
                            .select(EduHomeworkAiReviewDO::getId, EduHomeworkAiReviewDO::getHomeworkRecordId,
                                    EduHomeworkAiReviewDO::getStatus)
                            .in(EduHomeworkAiReviewDO::getHomeworkRecordId, chunk));
            for (EduHomeworkAiReviewDO review : reviews) {
                Long recordId = review.getHomeworkRecordId();
                if (recordId == null || review.getId() == null) {
                    continue;
                }
                Long current = latestId.get(recordId);
                if (current == null || review.getId() > current) {
                    latestId.put(recordId, review.getId());
                    result.put(recordId, review.getStatus());
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduHomeworkRecordGradeRespVO correctRecord(EduHomeworkRecordCorrectReqVO correctReqVO) {
        EduHomeworkRecordDO record = validateRecordExists(correctReqVO.getId());
        EduHomeworkDO homework = validateRecordAccess(record);
        validateHomeworkNotArchived(homework);
        if (!EduHomeworkRecordStatusEnum.CORRECTABLE.contains(record.getStatus())) {
            throw exception(HOMEWORK_RECORD_CORRECT_STATUS_INVALID);
        }
        Long operatorId = getLoginUserId();
        // 连点或网络重发：同一教师刚保存过同样的分数和评语，返回那一次的结果，不再写轨迹、不再排通知
        if (Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.CORRECTED)
                && Objects.equals(record.getScore(), correctReqVO.getScore())
                && Objects.equals(normalizeComment(record.getComment()), normalizeComment(correctReqVO.getComment()))) {
            EduHomeworkGradingActionDO repeat = gradingActionService.findRepeat(record, ACTION_CORRECT, operatorId);
            if (repeat != null) {
                return buildGradeResp(record, repeat);
            }
        }
        validateExpectedUpdateTime(record, correctReqVO.getExpectedUpdateTime());
        EduHomeworkAiReviewDO review = eduHomeworkAiReviewService.getLatestForCorrection(record.getId());
        LocalDateTime now = nextUpdateTime(record);
        int updated = eduHomeworkRecordMapper.update(stampUpdateTime(now),
                new LambdaUpdateWrapper<EduHomeworkRecordDO>()
                        .set(EduHomeworkRecordDO::getScore, correctReqVO.getScore())
                        .set(EduHomeworkRecordDO::getComment, correctReqVO.getComment())
                        .set(EduHomeworkRecordDO::getCorrectType, 1) // 手动批改
                        .set(EduHomeworkRecordDO::getCorrectTime, now.withNano(0))
                        .set(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.CORRECTED)
                        .eq(EduHomeworkRecordDO::getId, record.getId())
                        .in(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.CORRECTABLE)
                        .eq(record.getUpdateTime() != null, EduHomeworkRecordDO::getUpdateTime, record.getUpdateTime()));
        if (updated == 0) {
            throw exception(HOMEWORK_RECORD_STATE_CHANGED);
        }
        eduHomeworkAiReviewService.markTeacherReviewed(
                correctReqVO.getId(),
                correctReqVO.getScore(),
                correctReqVO.getComment());
        // 只有草案曾供教师参考时才关联；处理结果由 markTeacherReviewed 写回。
        Long aiReviewId = review != null && Objects.equals(review.getStatus(), HomeworkAiReviewStatusEnum.READY)
                ? review.getId() : null;
        Long historyId = historyService.append(record, "CORRECT", EduHomeworkRecordStatusEnum.CORRECTED,
                correctReqVO.getScore(), correctReqVO.getComment(), record.getRedoDeadline(), aiReviewId);
        EduHomeworkGradingActionDO action = gradingActionService.record(record, ACTION_CORRECT,
                EduHomeworkRecordStatusEnum.CORRECTED, now, historyId, operatorId, review,
                EduBusinessNotifyService.HOMEWORK_CORRECTED,
                correctedNotifyParams(homework, correctReqVO.getScore(), correctReqVO.getComment()),
                "/student/homework/feedback/" + homework.getId());

        EduHomeworkRecordDO after = copyRecord(record);
        after.setScore(correctReqVO.getScore());
        after.setComment(correctReqVO.getComment());
        after.setCorrectType(1);
        after.setCorrectTime(now.withNano(0));
        after.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        after.setUpdateTime(now);
        return buildGradeResp(after, action);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduHomeworkRecordGradeRespVO redoRecord(Long id, LocalDateTime redoDeadline, LocalDateTime expectedUpdateTime) {
        EduHomeworkRecordDO record = validateRecordExists(id);
        EduHomeworkDO homework = validateRecordAccess(record);
        validateHomeworkNotArchived(homework);
        validateRedoDeadline(homework, redoDeadline);
        return doRedoRecord(homework, record, redoDeadline, expectedUpdateTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void redoRecordBatch(Collection<Long> ids, LocalDateTime redoDeadline) {
        Set<Long> uniqueIds = ids == null ? Collections.emptySet() : ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            throw exception(HOMEWORK_RECORD_NOT_EXISTS);
        }
        // 先完成全部记录的归属与期限校验，任一越权则整体失败，不做部分打回
        List<EduHomeworkRecordDO> records = new ArrayList<>(uniqueIds.size());
        Map<Long, EduHomeworkDO> homeworks = new HashMap<>();
        for (Long id : uniqueIds) {
            EduHomeworkRecordDO record = validateRecordExists(id);
            EduHomeworkDO homework = validateRecordAccess(record);
            validateHomeworkNotArchived(homework);
            validateRedoDeadline(homework, redoDeadline);
            records.add(record);
            homeworks.put(record.getId(), homework);
        }
        // 任一记录在此期间被他人改动则整体回滚
        records.forEach(record -> doRedoRecord(homeworks.get(record.getId()), record, redoDeadline, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduHomeworkRecordGradeRespVO undoGrading(Long undoToken) {
        EduHomeworkGradingActionDO action = gradingActionService.getAction(undoToken);
        Long operatorId = getLoginUserId();
        // 只能撤销自己的操作；别人的凭据按“找不到”处理，不暴露是否存在
        if (action == null || operatorId == null || !Objects.equals(action.getOperatorId(), operatorId)) {
            throw exception(HOMEWORK_GRADING_UNDO_NOT_EXISTS);
        }
        EduHomeworkRecordDO record = validateRecordExists(action.getRecordId());
        validateHomeworkNotArchived(validateRecordAccess(record));
        LocalDateTime now = nextUpdateTime(record);
        // 先抢占通知：与发送任务互斥，抢到后通知就不会再发
        if (!gradingActionService.cancelIfUndoable(action, now)) {
            throw exception(HOMEWORK_GRADING_UNDO_EXPIRED);
        }
        LambdaUpdateWrapper<EduHomeworkRecordDO> restore = new LambdaUpdateWrapper<EduHomeworkRecordDO>()
                .set(EduHomeworkRecordDO::getStatus, action.getPrevStatus())
                .set(EduHomeworkRecordDO::getScore, action.getPrevScore())
                .set(EduHomeworkRecordDO::getComment, action.getPrevComment())
                .set(EduHomeworkRecordDO::getCorrectType, action.getPrevCorrectType())
                .set(EduHomeworkRecordDO::getCorrectTime, action.getPrevCorrectTime())
                .set(EduHomeworkRecordDO::getRedoDeadline, action.getPrevRedoDeadline())
                .set(action.getPrevRedoCount() != null, EduHomeworkRecordDO::getRedoCount, action.getPrevRedoCount())
                .eq(EduHomeworkRecordDO::getId, record.getId())
                .eq(EduHomeworkRecordDO::getStatus, action.getAfterStatus())
                .eq(EduHomeworkRecordDO::getUpdateTime, action.getAfterUpdateTime());
        // 操作之后记录又被改过（学生重交、他人批改）就不恢复，抢占的通知随事务回滚，照常发出
        if (eduHomeworkRecordMapper.update(stampUpdateTime(now), restore) == 0) {
            throw exception(HOMEWORK_GRADING_UNDO_CHANGED);
        }
        gradingActionService.restoreAiReview(action);
        historyService.append(record, HISTORY_UNDO, action.getPrevStatus(), action.getPrevScore(),
                action.getPrevComment(), action.getPrevRedoDeadline(), null);

        EduHomeworkRecordDO after = copyRecord(record);
        after.setStatus(action.getPrevStatus());
        after.setScore(action.getPrevScore());
        after.setComment(action.getPrevComment());
        after.setCorrectType(action.getPrevCorrectType());
        after.setCorrectTime(action.getPrevCorrectTime());
        after.setRedoDeadline(action.getPrevRedoDeadline());
        if (action.getPrevRedoCount() != null) {
            after.setRedoCount(action.getPrevRedoCount());
        }
        after.setUpdateTime(now);
        return buildGradeResp(after, null);
    }

    @Override
    public void deleteByHomeworkId(Long homeworkId) {
        eduHomeworkRecordMapper.delete(new LambdaQueryWrapper<EduHomeworkRecordDO>()
                .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId));
    }

    private EduHomeworkRecordGradeRespVO doRedoRecord(EduHomeworkDO homework, EduHomeworkRecordDO record,
                                                      LocalDateTime redoDeadline, LocalDateTime expectedUpdateTime) {
        // 班级、课程或班课归档后成绩只读，不再打回
        archiveAccessService.validateRedoScopeActive(record.getHomeworkId());
        if (!REDO_ALLOWED.contains(record.getStatus())) {
            throw exception(HOMEWORK_RECORD_REDO_STATUS_INVALID);
        }
        Long operatorId = getLoginUserId();
        if (Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.REDO)) {
            return adjustRedoDeadline(record, redoDeadline, expectedUpdateTime, operatorId);
        }
        validateExpectedUpdateTime(record, expectedUpdateTime);
        EduHomeworkAiReviewDO review = eduHomeworkAiReviewService.getLatestForCorrection(record.getId());
        LocalDateTime now = nextUpdateTime(record);
        int updated = eduHomeworkRecordMapper.update(stampUpdateTime(now),
                new LambdaUpdateWrapper<EduHomeworkRecordDO>()
                        .set(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.REDO)
                        .set(EduHomeworkRecordDO::getRedoDeadline, redoDeadline)
                        .setSql("redo_count = redo_count + 1")
                        .eq(EduHomeworkRecordDO::getId, record.getId())
                        .in(EduHomeworkRecordDO::getStatus, REDO_FROM)
                        .eq(record.getUpdateTime() != null, EduHomeworkRecordDO::getUpdateTime, record.getUpdateTime()));
        // 状态确实变为打回后，才拒绝 AI 草案并排队通知学生
        if (updated == 0) {
            throw exception(HOMEWORK_RECORD_STATE_CHANGED);
        }
        Long historyId = historyService.append(record, "REDO", EduHomeworkRecordStatusEnum.REDO,
                record.getScore(), record.getComment(), redoDeadline, null);
        eduHomeworkAiReviewService.rejectLatestReview(record.getId());
        EduHomeworkGradingActionDO action = gradingActionService.record(record, ACTION_REDO,
                EduHomeworkRecordStatusEnum.REDO, now, historyId, operatorId, review,
                EduBusinessNotifyService.HOMEWORK_REDO,
                Map.of("homeworkName", Objects.toString(homework.getHomeworkName(), "未命名任务")),
                "/student/homework/submit/" + homework.getId());

        EduHomeworkRecordDO after = copyRecord(record);
        after.setStatus(EduHomeworkRecordStatusEnum.REDO);
        after.setRedoDeadline(redoDeadline);
        after.setRedoCount((record.getRedoCount() == null ? 0 : record.getRedoCount()) + 1);
        after.setUpdateTime(now);
        return buildGradeResp(after, action);
    }

    /**
     * 已处于打回状态时只调整重做截止时间，避免重做期过后学生再次无法重交；不通知学生，也不提供撤销。
     */
    private EduHomeworkRecordGradeRespVO adjustRedoDeadline(EduHomeworkRecordDO record, LocalDateTime redoDeadline,
                                                            LocalDateTime expectedUpdateTime, Long operatorId) {
        if (redoDeadline == null || sameSecond(record.getRedoDeadline(), redoDeadline)) {
            // 期限没变：可能是打回按钮连点，刚打回的那次仍可撤销
            return buildGradeResp(record, gradingActionService.findRepeat(record, ACTION_REDO, operatorId));
        }
        validateExpectedUpdateTime(record, expectedUpdateTime);
        LocalDateTime now = nextUpdateTime(record);
        int updated = eduHomeworkRecordMapper.update(stampUpdateTime(now),
                new LambdaUpdateWrapper<EduHomeworkRecordDO>()
                        .set(EduHomeworkRecordDO::getRedoDeadline, redoDeadline)
                        .eq(EduHomeworkRecordDO::getId, record.getId())
                        .eq(EduHomeworkRecordDO::getStatus, EduHomeworkRecordStatusEnum.REDO)
                        .eq(record.getUpdateTime() != null, EduHomeworkRecordDO::getUpdateTime, record.getUpdateTime()));
        if (updated == 0) {
            throw exception(HOMEWORK_RECORD_STATE_CHANGED);
        }
        historyService.append(record, "REDO_DEADLINE", EduHomeworkRecordStatusEnum.REDO, record.getScore(),
                record.getComment(), redoDeadline, null);
        EduHomeworkRecordDO after = copyRecord(record);
        after.setRedoDeadline(redoDeadline);
        after.setUpdateTime(now);
        return buildGradeResp(after, null);
    }

    private Map<String, Object> correctedNotifyParams(EduHomeworkDO homework, Integer score, String comment) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeworkName", homework.getHomeworkName());
        params.put("score", score);
        params.put("comment", comment == null || comment.isBlank() ? "无" : comment);
        return params;
    }

    /** 传入了读取时的更新时间，而记录已被更新（他人批改、学生重交、成绩导入）时拒绝。 */
    private static void validateExpectedUpdateTime(EduHomeworkRecordDO record, LocalDateTime expectedUpdateTime) {
        if (expectedUpdateTime != null && record.getUpdateTime() != null
                && !Objects.equals(record.getUpdateTime(), expectedUpdateTime)) {
            throw exception(HOMEWORK_RECORD_CONFLICT);
        }
    }

    private EduHomeworkRecordGradeRespVO buildGradeResp(EduHomeworkRecordDO record, EduHomeworkGradingActionDO action) {
        EduHomeworkRecordGradeRespVO resp = new EduHomeworkRecordGradeRespVO();
        resp.setId(record.getId());
        resp.setStatus(record.getStatus());
        resp.setScore(record.getScore());
        resp.setComment(record.getComment());
        resp.setCorrectType(record.getCorrectType());
        resp.setCorrectTime(record.getCorrectTime());
        resp.setRedoCount(record.getRedoCount());
        resp.setRedoDeadline(record.getRedoDeadline());
        resp.setUpdateTime(record.getUpdateTime());
        // 归属已在调用方校验，这里直接取最新初评，不再重复校验
        EduHomeworkAiReviewDO review = eduHomeworkAiReviewService.getLatestForCorrection(record.getId());
        resp.setAiReviewStatus(review == null ? null : review.getStatus());
        int remaining = gradingActionService.remainingSeconds(action, LocalDateTime.now());
        if (action != null && remaining > 0) {
            resp.setUndoToken(action.getId());
            resp.setUndoDeadline(action.getUndoDeadline());
            resp.setUndoSeconds(remaining);
        }
        return resp;
    }

    /** 本次写入的 update_time：与 datetime(3) 对齐，同一毫秒内再次操作也必须推进版本。 */
    private static LocalDateTime nextUpdateTime(EduHomeworkRecordDO record) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime previous = record.getUpdateTime();
        return previous != null && !now.isAfter(previous)
                ? previous.truncatedTo(ChronoUnit.MILLIS).plus(1, ChronoUnit.MILLIS) : now;
    }

    private static EduHomeworkRecordDO stampUpdateTime(LocalDateTime now) {
        EduHomeworkRecordDO change = new EduHomeworkRecordDO();
        change.setUpdateTime(now);
        return change;
    }

    private static EduHomeworkRecordDO copyRecord(EduHomeworkRecordDO source) {
        EduHomeworkRecordDO copy = new EduHomeworkRecordDO();
        copy.setId(source.getId());
        copy.setHomeworkId(source.getHomeworkId());
        copy.setStudentId(source.getStudentId());
        copy.setStatus(source.getStatus());
        copy.setScore(source.getScore());
        copy.setComment(source.getComment());
        copy.setCorrectType(source.getCorrectType());
        copy.setCorrectTime(source.getCorrectTime());
        copy.setRedoCount(source.getRedoCount());
        copy.setRedoDeadline(source.getRedoDeadline());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private static String normalizeComment(String comment) {
        return comment == null ? "" : comment.trim();
    }

    private static boolean sameSecond(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.truncatedTo(ChronoUnit.SECONDS).equals(b.truncatedTo(ChronoUnit.SECONDS));
    }

    private EduHomeworkDO validateHomeworkExists(Long homeworkId) {
        EduHomeworkDO homework = eduHomeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        return homework;
    }

    private EduHomeworkRecordDO validateRecordExists(Long id) {
        if (id == null) {
            throw exception(HOMEWORK_RECORD_NOT_EXISTS);
        }
        EduHomeworkRecordDO record = eduHomeworkRecordMapper.selectById(id);
        if (record == null) {
            throw exception(HOMEWORK_RECORD_NOT_EXISTS);
        }
        return record;
    }

    private EduHomeworkDO validateRecordAccess(EduHomeworkRecordDO record) {
        if (record == null) {
            throw exception(HOMEWORK_RECORD_NOT_EXISTS);
        }
        EduHomeworkDO homework = validateHomeworkExists(record.getHomeworkId());
        validateHomeworkAccess(homework);
        return homework;
    }

    private void validateHomeworkNotArchived(EduHomeworkDO homework) {
        if (Objects.equals(homework.getStatus(), EduHomeworkStatusEnum.ARCHIVED)) {
            throw exception(HOMEWORK_ARCHIVED);
        }
    }

    /**
     * 校验重做截止时间：设置时必须晚于当前时间；任务已截止时必须设置，否则学生无法重新提交。
     */
    private void validateRedoDeadline(EduHomeworkDO homework, LocalDateTime redoDeadline) {
        if (redoDeadline != null) {
            if (!redoDeadline.isAfter(LocalDateTime.now())) {
                throw exception(HOMEWORK_RECORD_REDO_DEADLINE_INVALID);
            }
            return;
        }
        if (HomeworkDeadlineUtils.isExpired(homework.getDeadline())) {
            throw exception(HOMEWORK_RECORD_REDO_DEADLINE_REQUIRED);
        }
    }

    private void validateDeadline(EduHomeworkDO homework, Long studentId) {
        if (homework.getDeadline() == null || !LocalDateTime.now().isAfter(homework.getDeadline())) {
            return;
        }
        LocalDateTime effective = HomeworkDeadlineUtils.withPersonalDeadline(homework.getDeadline(),
                homeworkExtensionService.getPersonalDeadline(homework.getId(), studentId));
        if (HomeworkDeadlineUtils.isExpired(effective)) {
            throw exception(HOMEWORK_RECORD_DEADLINE_EXCEEDED);
        }
    }

    private void validateNotDuplicate(Long homeworkId, Long studentId) {
        EduHomeworkRecordDO existing = eduHomeworkRecordMapper.selectOne(
                new LambdaQueryWrapper<EduHomeworkRecordDO>()
                        .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId)
                        .eq(EduHomeworkRecordDO::getStudentId, studentId));
        if (existing != null) {
            throw exception(HOMEWORK_RECORD_ALREADY_SUBMITTED);
        }
    }

    private void validateAssignee(Long homeworkId, Long studentId) {
        if (eduHomeworkAssigneeMapper.selectByHomeworkIdAndStudentId(homeworkId, studentId) == null) {
            throw exception(STUDENT_HOMEWORK_NOT_ASSIGNED);
        }
    }

    private void validateHomeworkAccess(EduHomeworkDO homework) {
        if (homework == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        Long loginUserId = getLoginUserId();
        if (loginUserId == null) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        if (HomeworkWriteAccess.isOwner(homework)) {
            return;
        }
        throw exception(HOMEWORK_NOT_EXISTS);
    }

}
