package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRemindRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkExtensionDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkNotifyStateDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRemindLogDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkExtensionMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkNotifyStateMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkNotifyStateMapper.DigestRow;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRemindLogMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRemindBatchMapper;
import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class EduHomeworkRemindServiceImpl implements EduHomeworkRemindService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 汇总只统计这么多分钟之前的提交，给仍在提交中的事务留出写入时间，避免漏计。 */
    static final int DIGEST_GRACE_MINUTES = 2;
    /** 任务没有截止时间时，催交通知里的截止时间 */
    static final String NO_DEADLINE_TEXT = "截止时间";

    @Resource
    private EduHomeworkMapper homeworkMapper;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduHomeworkRecordMapper recordMapper;
    @Resource
    private EduHomeworkExtensionMapper extensionMapper;
    @Resource
    private EduHomeworkRemindLogMapper remindLogMapper;
    @Resource
    private EduHomeworkRemindBatchMapper remindBatchMapper;
    @Resource
    private EduHomeworkNotifyStateMapper notifyStateMapper;
    @Resource
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private EduRemindPolicy remindPolicy;

    private Clock clock = Clock.systemDefaultZone();

    @VisibleForTesting
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ========== 教师催交 ==========

    @Override
    public EduHomeworkRemindRespVO remindUnsubmitted(Long homeworkId) {
        EduHomeworkDO homework = homeworkId == null ? null : homeworkMapper.selectById(homeworkId);
        Long loginUserId = getLoginUserId();
        if (homework == null || loginUserId == null || !Objects.equals(homework.getTeacherId(), loginUserId)) {
            throw exception(HOMEWORK_NOT_EXISTS);
        }
        if (!archiveAccessService.canSubmit(homework)) {
            throw exception(HOMEWORK_REMIND_STATUS_INVALID);
        }
        // 数据库 datetime 只到秒，截到秒才能按值比较和恢复。
        LocalDateTime now = now().truncatedTo(ChronoUnit.SECONDS);
        List<RemindTarget> targets = listUnsubmittedTargets(homework).stream()
                .filter(target -> target.deadline() == null || target.deadline().isAfter(now))
                .toList();
        if (targets.isEmpty()) {
            return new EduHomeworkRemindRespVO(0, 0, null);
        }
        int interval = remindPolicy.manualRemindIntervalMinutes();
        LocalDateTime previous = claimManualRemind(homework.getId(), now, interval);
        int notified = 0;
        for (RemindTarget target : targets) {
            if (sendRemind(homework, target)) {
                notified++;
            }
        }
        if (notified == 0) {
            // 一条都没发出去（通知服务异常）时不占用间隔，教师可以马上重试。
            notifyStateMapper.releaseManualRemind(homework.getId(), now, previous);
            return new EduHomeworkRemindRespVO(targets.size(), 0, null);
        }
        return new EduHomeworkRemindRespVO(targets.size(), notified, interval > 0 ? now.plusMinutes(interval) : null);
    }

    /**
     * 抢占本任务的催交时间。
     *
     * @return 抢占前的上次催交时间，用于发送全部失败时恢复
     */
    private LocalDateTime claimManualRemind(Long homeworkId, LocalDateTime now, int intervalMinutes) {
        EduHomeworkNotifyStateDO state = ensureState(homeworkId);
        LocalDateTime previous = state != null ? state.getManualRemindTime() : null;
        LocalDateTime allowedBefore = now.minusMinutes(intervalMinutes);
        if (notifyStateMapper.claimManualRemind(homeworkId, now, allowedBefore) > 0) {
            return previous;
        }
        EduHomeworkNotifyStateDO latest = notifyStateMapper.selectByHomeworkId(homeworkId);
        LocalDateTime last = latest != null && latest.getManualRemindTime() != null ? latest.getManualRemindTime() : now;
        throw exception(HOMEWORK_REMIND_TOO_FREQUENT, intervalMinutes,
                last.plusMinutes(intervalMinutes).format(TIME_FORMAT));
    }

    // ========== 截止前自动提醒 ==========

    @Override
    public String remindBeforeDeadline() {
        if (!remindPolicy.remindEnabled()) {
            return "截止前提醒已关闭";
        }
        LocalDateTime now = now();
        LocalDateTime until = now.plusHours(remindPolicy.remindBeforeHours());
        List<EduHomeworkDO> candidates = selectRemindCandidates(now, until);
        int homeworkCount = 0;
        int remindedCount = 0;
        for (EduHomeworkDO homework : candidates) {
            if (!archiveAccessService.canSubmit(homework)) {
                continue;
            }
            List<RemindTarget> due = listUnsubmittedTargets(homework).stream()
                    .filter(target -> isWithin(target.deadline(), now, until))
                    .toList();
            if (due.isEmpty()) {
                continue;
            }
            homeworkCount++;
            Set<Long> reminded = remindLogMapper.selectListByHomeworkIdAndType(homework.getId(),
                            EduHomeworkRemindLogDO.TYPE_AUTO).stream()
                    .map(EduHomeworkRemindLogDO::getStudentId)
                    .collect(Collectors.toSet());
            List<EduHomeworkRemindLogDO> pending = due.stream()
                    .filter(target -> !reminded.contains(target.studentId()))
                    .map(target -> {
                        EduHomeworkRemindLogDO row = new EduHomeworkRemindLogDO();
                        row.setHomeworkId(homework.getId());
                        row.setStudentId(target.studentId());
                        row.setRemindType(EduHomeworkRemindLogDO.TYPE_AUTO);
                        row.setDeadline(target.deadline());
                        return row;
                    }).toList();
            List<EduHomeworkRemindLogDO> claimed = new ArrayList<>();
            for (List<EduHomeworkRemindLogDO> rows : CollUtil.split(pending, 500)) {
                try {
                    remindBatchMapper.insertLogs(rows, Objects.toString(getLoginUserId(), null));
                    claimed.addAll(rows);
                } catch (DuplicateKeyException ex) {
                    // 批次冲突不代表所有人都已提醒；只在竞争路径逐条抢占，保留未冲突的学生。
                    for (EduHomeworkRemindLogDO row : rows) {
                        row.setId(null);
                        try {
                            remindLogMapper.insert(row);
                            claimed.add(row);
                        } catch (DuplicateKeyException ignored) {
                            // 唯一键已经被另一轮任务占用，不重复发送。
                        }
                    }
                }
            }
            Map<String, List<EduHomeworkRemindLogDO>> groups = claimed.stream().collect(Collectors.groupingBy(
                    row -> row.getDeadline().format(TIME_FORMAT), LinkedHashMap::new, Collectors.toList()));
            List<Long> failedIds = new ArrayList<>();
            for (Map.Entry<String, List<EduHomeworkRemindLogDO>> group : groups.entrySet()) {
                List<Long> recipients = group.getValue().stream().map(EduHomeworkRemindLogDO::getStudentId).toList();
                Set<Long> successful = new HashSet<>(businessNotifyService.sendToUsersWithResult(recipients,
                        EduBusinessNotifyService.HOMEWORK_REMIND,
                        Map.of("homeworkName", Objects.toString(homework.getHomeworkName(), "未命名任务"), "deadline", group.getKey()),
                        "/student/homework/submit/" + homework.getId()));
                remindedCount += successful.size();
                group.getValue().stream().filter(row -> !successful.contains(row.getStudentId()))
                        .map(EduHomeworkRemindLogDO::getId).forEach(failedIds::add);
            }
            for (List<Long> ids : CollUtil.split(failedIds, 500)) remindBatchMapper.deleteFailedLogs(ids);
        }
        return String.format("截止前提醒：%d 个任务，提醒 %d 名学生", homeworkCount, remindedCount);
    }

    /** 任务截止时间或某个学生的个人截止时间落在 (now, until] 内的已发布任务 */
    private List<EduHomeworkDO> selectRemindCandidates(LocalDateTime now, LocalDateTime until) {
        Map<Long, EduHomeworkDO> candidates = new LinkedHashMap<>();
        homeworkMapper.selectList(new LambdaQueryWrapperX<EduHomeworkDO>()
                        .eq(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED)
                        .gt(EduHomeworkDO::getDeadline, now)
                        .le(EduHomeworkDO::getDeadline, until))
                .forEach(homework -> candidates.put(homework.getId(), homework));
        Set<Long> extendedHomeworkIds = extensionMapper.selectListByDeadlineBetween(now, until).stream()
                .map(EduHomeworkExtensionDO::getHomeworkId)
                .filter(id -> !candidates.containsKey(id))
                .collect(Collectors.toSet());
        if (!extendedHomeworkIds.isEmpty()) {
            homeworkMapper.selectList(new LambdaQueryWrapperX<EduHomeworkDO>()
                            .in(EduHomeworkDO::getId, extendedHomeworkIds)
                            .eq(EduHomeworkDO::getStatus, EduHomeworkStatusEnum.PUBLISHED)
                            .isNotNull(EduHomeworkDO::getDeadline))
                    .forEach(homework -> candidates.put(homework.getId(), homework));
        }
        return new ArrayList<>(candidates.values());
    }

    // ========== 教师新提交汇总 ==========

    @Override
    public String sendTeacherDigests() {
        int minutes = remindPolicy.teacherDigestMinutes();
        if (minutes <= 0) {
            return "教师新提交汇总已关闭";
        }
        LocalDateTime now = now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutoff = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(DIGEST_GRACE_MINUTES);
        LocalDateTime since = cutoff.minusMinutes(minutes);
        LocalDateTime gateAfter = now.minusMinutes(minutes);
        notifyStateMapper.initializeGlobalDigestCursor(since);
        LocalDateTime scanSince = Objects.requireNonNullElse(notifyStateMapper.selectGlobalDigestCursor(), since);
        notifyStateMapper.initializeDigestCursors(scanSince);
        notifyStateMapper.advanceEmptyDigestCursors(cutoff);
        List<DigestRow> rows = notifyStateMapper.selectPendingDigests(cutoff, gateAfter);
        int notified = 0;
        for (DigestRow row : rows) {
            EduHomeworkDO homework = homeworkMapper.selectById(row.getHomeworkId());
            if (homework == null || homework.getTeacherId() == null || row.getSubmittedCount() == null
                    || row.getSubmittedCount() <= 0) {
                continue;
            }
            if (notifyStateMapper.advanceDigest(row.getHomeworkId(), row.getDigestUntil(), cutoff, now, gateAfter) <= 0) {
                continue;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("homeworkName", Objects.toString(homework.getHomeworkName(), "未命名任务"));
            params.put("count", row.getSubmittedCount());
            long resubmitCount = Objects.requireNonNullElse(row.getResubmittedCount(), 0L);
            params.put("resubmitNote", resubmitCount > 0 ? "（含 " + resubmitCount + " 份重交）" : "");
            if (businessNotifyService.sendToUser(homework.getTeacherId(),
                    EduBusinessNotifyService.TEACHER_HOMEWORK_SUBMITTED, params,
                    "/capacity/homework_manager/homework/correct/" + homework.getId())) {
                notified++;
            }
        }
        notifyStateMapper.advanceGlobalDigestCursor(cutoff);
        return String.format("教师新提交汇总：通知 %d 个任务", notified);
    }

    // ========== 公共 ==========

    /** 任务的未提交学生及各自的实际截止时间（任务截止时间与个人延期取较晚） */
    @VisibleForTesting
    List<RemindTarget> listUnsubmittedTargets(EduHomeworkDO homework) {
        List<Long> assigned = assigneeMapper.selectListByHomeworkId(homework.getId()).stream()
                .map(EduHomeworkAssigneeDO::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (assigned.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> submitted = recordMapper.selectList(new LambdaQueryWrapperX<EduHomeworkRecordDO>()
                        .select(EduHomeworkRecordDO::getStudentId)
                        .eq(EduHomeworkRecordDO::getHomeworkId, homework.getId())).stream()
                .map(EduHomeworkRecordDO::getStudentId)
                .collect(Collectors.toSet());
        Map<Long, LocalDateTime> extensions = extensionMapper.selectListByHomeworkId(homework.getId()).stream()
                .collect(Collectors.toMap(EduHomeworkExtensionDO::getStudentId, EduHomeworkExtensionDO::getDeadline,
                        (left, right) -> left));
        return assigned.stream()
                .filter(studentId -> !submitted.contains(studentId))
                .map(studentId -> new RemindTarget(studentId,
                        HomeworkDeadlineUtils.withPersonalDeadline(homework.getDeadline(), extensions.get(studentId))))
                .toList();
    }

    private boolean sendRemind(EduHomeworkDO homework, RemindTarget target) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeworkName", Objects.toString(homework.getHomeworkName(), "未命名任务"));
        params.put("deadline", target.deadline() == null ? NO_DEADLINE_TEXT : target.deadline().format(TIME_FORMAT));
        return businessNotifyService.sendToUser(target.studentId(), EduBusinessNotifyService.HOMEWORK_REMIND,
                params, "/student/homework/submit/" + homework.getId());
    }

    private EduHomeworkNotifyStateDO ensureState(Long homeworkId) {
        EduHomeworkNotifyStateDO state = notifyStateMapper.selectByHomeworkId(homeworkId);
        if (state != null) {
            return state;
        }
        EduHomeworkNotifyStateDO created = new EduHomeworkNotifyStateDO();
        created.setHomeworkId(homeworkId);
        try {
            notifyStateMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            return notifyStateMapper.selectByHomeworkId(homeworkId);
        }
    }

    private static boolean isWithin(LocalDateTime deadline, LocalDateTime from, LocalDateTime until) {
        return deadline != null && deadline.isAfter(from) && !deadline.isAfter(until);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 一名待提醒的学生及其实际截止时间（为空表示不限） */
    record RemindTarget(Long studentId, LocalDateTime deadline) {
    }

}
