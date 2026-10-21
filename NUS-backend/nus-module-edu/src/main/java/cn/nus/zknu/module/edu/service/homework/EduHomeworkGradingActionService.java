package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.util.json.JsonUtils;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkGradingActionDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkGradingActionMapper;
import cn.nus.zknu.module.edu.service.aireview.HomeworkAiReviewDispatcher;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.*;

/**
 * 批改、打回的撤销与延时通知。
 * <p>
 * 每次批改或打回记一行操作：保存操作前的快照用于撤销，学生通知等撤销期过后再由 {@link EduGradingNotifySender} 发出。
 * 撤销和发送都只从“待发送”状态出发做条件更新，二者只有一方能成功，重启后未发送的通知照常补发。
 */
@Service
@Slf4j
public class EduHomeworkGradingActionService {

    /** 发送任务比撤销截止多等的秒数，避免临界时刻撤销请求还在路上。 */
    static final int UNDO_GRACE_SECONDS = 3;
    /** 每轮最多发送的通知数。 */
    static final int SEND_BATCH_SIZE = 200;

    @Resource
    private EduHomeworkGradingActionMapper actionMapper;
    @Resource
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Resource
    private EduGradingPolicy gradingPolicy;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private HomeworkAiReviewDispatcher aiReviewDispatcher;

    /**
     * 记一次批改或打回，通知排队到撤销期之后。
     *
     * @param before          操作前读到的提交记录
     * @param afterUpdateTime 操作写入的 update_time，撤销时据此判断记录有没有再变
     * @param aiBefore        操作前最新的 AI 初评，可为空
     */
    public EduHomeworkGradingActionDO record(EduHomeworkRecordDO before, String actionType, int afterStatus,
                                             LocalDateTime afterUpdateTime, Long historyId, Long operatorId,
                                             EduHomeworkAiReviewDO aiBefore, String notifyTemplate,
                                             Map<String, Object> notifyParams, String notifyUrl) {
        EduHomeworkGradingActionDO action = new EduHomeworkGradingActionDO();
        action.setRecordId(before.getId());
        action.setHomeworkId(before.getHomeworkId());
        action.setStudentId(before.getStudentId());
        action.setActionType(actionType);
        action.setHistoryId(historyId);
        action.setOperatorId(operatorId);
        action.setPrevStatus(before.getStatus());
        action.setPrevScore(before.getScore());
        action.setPrevComment(before.getComment());
        action.setPrevCorrectType(before.getCorrectType());
        action.setPrevCorrectTime(before.getCorrectTime());
        action.setPrevRedoCount(before.getRedoCount());
        action.setPrevRedoDeadline(before.getRedoDeadline());
        action.setAfterStatus(afterStatus);
        action.setAfterUpdateTime(afterUpdateTime);
        if (aiBefore != null) {
            action.setAiReviewId(aiBefore.getId());
            action.setAiPrevStatus(aiBefore.getStatus());
            action.setAiPrevDecision(aiBefore.getTeacherDecision());
            action.setAiPrevTeacherScore(aiBefore.getTeacherScore());
            action.setAiPrevTeacherComment(aiBefore.getTeacherComment());
        }
        action.setUndoDeadline(LocalDateTime.now().plusSeconds(gradingPolicy.undoSeconds()).withNano(0));
        action.setNotifyStatus(NOTIFY_PENDING);
        action.setNotifyTemplate(notifyTemplate);
        action.setNotifyParams(JsonUtils.toJsonString(notifyParams == null ? Map.of() : notifyParams));
        action.setNotifyUrl(notifyUrl);
        actionMapper.insert(action);
        return action;
    }

    public EduHomeworkGradingActionDO getAction(Long id) {
        return id == null ? null : actionMapper.selectById(id);
    }

    /**
     * 连点保护：同一教师刚对这条记录做过同样的操作（还在撤销期内，记录也没再变），返回那次操作，调用方不再重复写入。
     */
    public EduHomeworkGradingActionDO findRepeat(EduHomeworkRecordDO current, String actionType, Long operatorId) {
        if (current == null || current.getId() == null || operatorId == null) {
            return null;
        }
        EduHomeworkGradingActionDO latest = actionMapper.selectLatestByRecordId(current.getId());
        if (latest == null
                || !Objects.equals(latest.getActionType(), actionType)
                || !Objects.equals(latest.getOperatorId(), operatorId)
                || !Objects.equals(latest.getNotifyStatus(), NOTIFY_PENDING)
                || !Objects.equals(latest.getAfterStatus(), current.getStatus())
                || !Objects.equals(latest.getAfterUpdateTime(), current.getUpdateTime())) {
            return null;
        }
        return latest;
    }

    /** 还能撤销的秒数；不能撤销时为 0。 */
    public int remainingSeconds(EduHomeworkGradingActionDO action, LocalDateTime now) {
        if (action == null || action.getUndoDeadline() == null
                || !Objects.equals(action.getNotifyStatus(), NOTIFY_PENDING)) {
            return 0;
        }
        long seconds = Duration.between(now, action.getUndoDeadline()).getSeconds();
        return (int) Math.max(0, seconds);
    }

    /**
     * 撤销前抢占通知：仍待发送且没过撤销期时改为“已撤销”。
     *
     * @return false 表示已过撤销期、已发送或已撤销
     */
    public boolean cancelIfUndoable(EduHomeworkGradingActionDO action, LocalDateTime now) {
        if (action == null || action.getUndoDeadline() == null
                || !Objects.equals(action.getNotifyStatus(), NOTIFY_PENDING)
                || now.isAfter(action.getUndoDeadline().plusSeconds(UNDO_GRACE_SECONDS))) {
            return false;
        }
        return actionMapper.transitFromPending(action.getId(), NOTIFY_UNDONE, now) == 1;
    }

    /**
     * 撤销时把 AI 初评恢复到操作前。只在初评仍是这次操作留下的“已采纳、已拒绝”时恢复，期间重新生成的初评不动。
     */
    public void restoreAiReview(EduHomeworkGradingActionDO action) {
        if (action == null || action.getAiReviewId() == null || action.getAiPrevStatus() == null) {
            return;
        }
        boolean requeue = Objects.equals(action.getAiPrevStatus(), HomeworkAiReviewStatusEnum.PROCESSING);
        LambdaUpdateWrapper<EduHomeworkAiReviewDO> update = new LambdaUpdateWrapper<EduHomeworkAiReviewDO>()
                .set(EduHomeworkAiReviewDO::getStatus,
                        requeue ? HomeworkAiReviewStatusEnum.QUEUED : action.getAiPrevStatus())
                .set(EduHomeworkAiReviewDO::getTeacherDecision, action.getAiPrevDecision())
                .set(EduHomeworkAiReviewDO::getTeacherScore, action.getAiPrevTeacherScore())
                .set(EduHomeworkAiReviewDO::getTeacherComment, action.getAiPrevTeacherComment())
                .eq(EduHomeworkAiReviewDO::getId, action.getAiReviewId())
                .in(EduHomeworkAiReviewDO::getStatus,
                        HomeworkAiReviewStatusEnum.ACCEPTED, HomeworkAiReviewStatusEnum.REJECTED);
        if (requeue) {
            // 原线程可能已经退出，换新令牌让迟到结果失效，并重新开始排队超时计时。
            update.set(EduHomeworkAiReviewDO::getGenerationToken, UUID.randomUUID().toString())
                    .set(EduHomeworkAiReviewDO::getUpdateTime, LocalDateTime.now());
        }
        if (aiReviewMapper.update(null, update) == 1 && requeue) {
            aiReviewDispatcher.dispatchAfterCommit(action.getAiReviewId());
        }
    }

    /**
     * 发出撤销期已过的通知。先抢占再发送：多实例或重复调度时同一条只会发一次；发送失败记为失败，不重发。
     *
     * @return 本轮发送成功的条数
     */
    public int sendDueNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<EduHomeworkGradingActionDO> due = actionMapper.selectDue(
                now.minusSeconds(UNDO_GRACE_SECONDS), SEND_BATCH_SIZE);
        int sent = 0;
        for (EduHomeworkGradingActionDO action : due) {
            if (actionMapper.transitFromPending(action.getId(), NOTIFY_SENT, now) != 1) {
                continue;
            }
            boolean ok = businessNotifyService.sendToUser(action.getStudentId(), action.getNotifyTemplate(),
                    parseParams(action.getNotifyParams()), action.getNotifyUrl());
            if (ok) {
                sent++;
                continue;
            }
            EduHomeworkGradingActionDO failed = new EduHomeworkGradingActionDO();
            failed.setId(action.getId());
            failed.setNotifyStatus(NOTIFY_FAILED);
            actionMapper.updateById(failed);
            log.warn("[sendDueNotifications][批改通知发送失败 actionId={}, recordId={}]",
                    action.getId(), action.getRecordId());
        }
        return sent;
    }

    private static Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> params = JsonUtils.parseObjectQuietly(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return params == null ? new LinkedHashMap<>() : params;
    }
}
