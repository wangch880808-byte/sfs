package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRemindRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkNotifyStateMapper.DigestRow;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EduHomeworkRemindServiceImplTest extends BaseMockitoUnitTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 10, 0, 30);
    private static final Long TEACHER_ID = 7L;

    @BeforeAll
    static void initTableInfo() {
        for (Class<?> type : List.of(EduHomeworkDO.class, EduHomeworkAssigneeDO.class, EduHomeworkRecordDO.class,
                EduHomeworkExtensionDO.class, EduHomeworkRemindLogDO.class, EduHomeworkNotifyStateDO.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @InjectMocks
    private EduHomeworkRemindServiceImpl service;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduHomeworkExtensionMapper extensionMapper;
    @Mock
    private EduHomeworkRemindLogMapper remindLogMapper;
    @Mock
    private EduHomeworkRemindBatchMapper remindBatchMapper;
    @Mock
    private EduHomeworkNotifyStateMapper notifyStateMapper;
    @Mock
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private EduRemindPolicy remindPolicy;

    @BeforeEach
    void fixClock() {
        ZoneId zone = ZoneId.systemDefault();
        service.setClock(Clock.fixed(NOW.atZone(zone).toInstant(), zone));
        lenient().when(archiveAccessService.canSubmit(any())).thenReturn(true);
    }

    // ========== 截止前自动提醒 ==========

    @Test
    void remindBeforeDeadline_selectsUnsubmittedStudentsWhoseEffectiveDeadlineIsWithinWindow() {
        when(remindPolicy.remindEnabled()).thenReturn(true);
        when(remindPolicy.remindBeforeHours()).thenReturn(24);
        EduHomeworkDO dueSoon = homework(1L, NOW.plusHours(5));
        EduHomeworkDO alreadyDue = homework(2L, NOW.minusHours(1));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(dueSoon), List.of(alreadyDue));
        when(extensionMapper.selectListByDeadlineBetween(NOW, NOW.plusHours(24)))
                .thenReturn(List.of(extension(2L, 20L, NOW.plusHours(2))));
        // 任务 1：10 未提交、11 已提交、12 延期到窗口之外、13 已经提醒过
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L, 11L, 12L, 13L));
        // 任务 2 已过任务截止时间：20 延期到 2 小时后，21 没有延期
        when(assigneeMapper.selectListByHomeworkId(2L)).thenReturn(assignees(2L, 20L, 21L));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 11L, 1)), List.of());
        when(extensionMapper.selectListByHomeworkId(1L)).thenReturn(List.of(extension(1L, 12L, NOW.plusDays(3))));
        when(extensionMapper.selectListByHomeworkId(2L)).thenReturn(List.of(extension(2L, 20L, NOW.plusHours(2))));
        when(remindLogMapper.selectListByHomeworkIdAndType(1L, EduHomeworkRemindLogDO.TYPE_AUTO))
                .thenReturn(List.of(remindLog(1L, 13L)));
        when(remindLogMapper.selectListByHomeworkIdAndType(2L, EduHomeworkRemindLogDO.TYPE_AUTO)).thenReturn(List.of());
        when(businessNotifyService.sendToUsersWithResult(any(), eq(EduBusinessNotifyService.HOMEWORK_REMIND), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = service.remindBeforeDeadline();

        assertEquals("截止前提醒：2 个任务，提醒 2 名学生", result);
        verify(businessNotifyService).sendToUsersWithResult(eq(List.of(10L)), eq(EduBusinessNotifyService.HOMEWORK_REMIND),
                eq(Map.of("homeworkName", "任务1", "deadline", "2026-10-10 15:00")), eq("/student/homework/submit/1"));
        verify(businessNotifyService).sendToUsersWithResult(eq(List.of(20L)), eq(EduBusinessNotifyService.HOMEWORK_REMIND),
                eq(Map.of("homeworkName", "任务2", "deadline", "2026-10-10 12:00")), eq("/student/homework/submit/2"));
        verify(businessNotifyService, times(2)).sendToUsersWithResult(any(), any(), any(), any());
        ArgumentCaptor<List<EduHomeworkRemindLogDO>> logs = ArgumentCaptor.forClass(List.class);
        verify(remindBatchMapper, times(2)).insertLogs(logs.capture(), any());
        assertEquals(List.of(10L, 20L), logs.getAllValues().stream().flatMap(List::stream).map(EduHomeworkRemindLogDO::getStudentId).toList());
        assertEquals(EduHomeworkRemindLogDO.TYPE_AUTO, logs.getValue().get(0).getRemindType());
        assertEquals(NOW.plusHours(2), logs.getValue().get(0).getDeadline());
    }

    @Test
    void remindBeforeDeadline_concurrentRunThatAlreadyClaimedTheStudentIsSkipped() {
        prepareSingleDueStudent();
        when(remindBatchMapper.insertLogs(any(), any())).thenThrow(new DuplicateKeyException("dup"));
        when(remindLogMapper.insert(any(EduHomeworkRemindLogDO.class))).thenThrow(new DuplicateKeyException("dup"));

        assertEquals("截止前提醒：1 个任务，提醒 0 名学生", service.remindBeforeDeadline());

        verify(businessNotifyService, never()).sendToUsersWithResult(any(), any(), any(), any());
    }

    @Test
    void remindBeforeDeadline_failedSendReleasesTheClaimForNextRun() {
        prepareSingleDueStudent();
        doAnswer(invocation -> {
            invocation.<List<EduHomeworkRemindLogDO>>getArgument(0).get(0).setId(99L);
            return 1;
        }).when(remindBatchMapper).insertLogs(any(), any());
        when(businessNotifyService.sendToUsersWithResult(eq(List.of(10L)), any(), any(), any())).thenReturn(List.of());

        assertEquals("截止前提醒：1 个任务，提醒 0 名学生", service.remindBeforeDeadline());

        verify(remindBatchMapper).deleteFailedLogs(List.of(99L));
    }

    @Test
    void remindBeforeDeadline_inactiveScopeIsSkipped() {
        when(remindPolicy.remindEnabled()).thenReturn(true);
        when(remindPolicy.remindBeforeHours()).thenReturn(24);
        EduHomeworkDO homework = homework(1L, NOW.plusHours(5));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(homework));
        when(archiveAccessService.canSubmit(homework)).thenReturn(false);

        assertEquals("截止前提醒：0 个任务，提醒 0 名学生", service.remindBeforeDeadline());

        verifyNoInteractions(assigneeMapper, remindLogMapper, businessNotifyService);
    }

    @Test
    void remindBeforeDeadline_disabledDoesNothing() {
        when(remindPolicy.remindEnabled()).thenReturn(false);

        assertEquals("截止前提醒已关闭", service.remindBeforeDeadline());

        verifyNoInteractions(homeworkMapper, extensionMapper, remindLogMapper, businessNotifyService);
    }

    @Test
    void automaticBatchReleasesOnlyFailedRecipientsAndSelectsOnlyStudentIds() {
        prepareSingleDueStudent();
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L, 11L, 12L));
        doAnswer(inv -> {
            List<EduHomeworkRemindLogDO> rows = inv.getArgument(0);
            for (int i = 0; i < rows.size(); i++) rows.get(i).setId(90L + i);
            return rows.size();
        }).when(remindBatchMapper).insertLogs(any(), any());
        when(businessNotifyService.sendToUsersWithResult(eq(List.of(10L, 11L, 12L)), any(), any(), any()))
                .thenReturn(List.of(10L, 12L));
        assertEquals("截止前提醒：1 个任务，提醒 2 名学生", service.remindBeforeDeadline());
        verify(remindBatchMapper).insertLogs(any(), any());
        verify(remindBatchMapper).deleteFailedLogs(List.of(91L));
        ArgumentCaptor<cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX<EduHomeworkRecordDO>> query = ArgumentCaptor.forClass(cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX.class);
        verify(recordMapper).selectList(query.capture());
        assertEquals("student_id", query.getValue().getSqlSelect());
        verify(remindLogMapper, never()).insert(any(EduHomeworkRemindLogDO.class));
    }

    @Test
    void conflictFallbackStillNotifiesOtherUnclaimedStudents() {
        prepareSingleDueStudent();
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L, 11L));
        when(remindBatchMapper.insertLogs(any(), any())).thenThrow(new DuplicateKeyException("竞争占位"));
        when(remindLogMapper.insert(any(EduHomeworkRemindLogDO.class))).thenAnswer(inv -> {
            EduHomeworkRemindLogDO row = inv.getArgument(0);
            if (row.getStudentId().equals(10L)) throw new DuplicateKeyException("已占用");
            row.setId(99L); return 1;
        });
        when(businessNotifyService.sendToUsersWithResult(eq(List.of(11L)), any(), any(), any())).thenReturn(List.of(11L));
        assertEquals("截止前提醒：1 个任务，提醒 1 名学生", service.remindBeforeDeadline());
        verify(remindBatchMapper, never()).deleteFailedLogs(any());
    }

    // ========== 教师催交 ==========

    @Test
    void remindUnsubmitted_notifiesStudentsWhoCanStillSubmit() {
        EduHomeworkDO homework = homework(1L, NOW.minusHours(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        // 10 有延期还能提交，11 已提交，12 已过截止时间
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L, 11L, 12L));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 11L, 2)));
        when(extensionMapper.selectListByHomeworkId(1L)).thenReturn(List.of(extension(1L, 10L, NOW.plusDays(1))));
        when(remindPolicy.manualRemindIntervalMinutes()).thenReturn(60);
        LocalDateTime claimed = NOW.withNano(0);
        when(notifyStateMapper.claimManualRemind(1L, claimed, claimed.minusMinutes(60))).thenReturn(1);
        when(businessNotifyService.sendToUser(eq(10L), eq(EduBusinessNotifyService.HOMEWORK_REMIND), any(), any()))
                .thenReturn(true);

        EduHomeworkRemindRespVO resp = asTeacher(() -> service.remindUnsubmitted(1L));

        assertEquals(1, resp.getTargetCount());
        assertEquals(1, resp.getNotifiedCount());
        assertEquals(claimed.plusMinutes(60), resp.getNextAvailableTime());
        verify(notifyStateMapper).insert(any(EduHomeworkNotifyStateDO.class));
        verify(businessNotifyService).sendToUser(eq(10L), any(),
                eq(Map.of("homeworkName", "任务1", "deadline", "2026-10-11 10:00")), any());
        verify(businessNotifyService, times(1)).sendToUser(any(), any(), any(), any());
    }

    @Test
    void remindUnsubmitted_withinIntervalIsRejectedWithNextTime() {
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1L, NOW.plusDays(1)));
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(remindPolicy.manualRemindIntervalMinutes()).thenReturn(60);
        EduHomeworkNotifyStateDO state = new EduHomeworkNotifyStateDO();
        state.setHomeworkId(1L);
        state.setManualRemindTime(NOW.minusMinutes(10).withNano(0));
        when(notifyStateMapper.selectByHomeworkId(1L)).thenReturn(state);
        when(notifyStateMapper.claimManualRemind(eq(1L), any(), any())).thenReturn(0);

        assertServiceException(() -> asTeacher(() -> service.remindUnsubmitted(1L)),
                HOMEWORK_REMIND_TOO_FREQUENT, 60, "2026-10-10 10:50");

        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
        verify(notifyStateMapper, never()).insert(any(EduHomeworkNotifyStateDO.class));
    }

    @Test
    void remindUnsubmitted_zeroIntervalNeverLimits() {
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1L, null));
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(remindPolicy.manualRemindIntervalMinutes()).thenReturn(0);
        LocalDateTime claimed = NOW.withNano(0);
        when(notifyStateMapper.claimManualRemind(1L, claimed, claimed)).thenReturn(1);
        when(businessNotifyService.sendToUser(eq(10L), any(), any(), any())).thenReturn(true);

        EduHomeworkRemindRespVO resp = asTeacher(() -> service.remindUnsubmitted(1L));

        assertEquals(1, resp.getNotifiedCount());
        assertNull(resp.getNextAvailableTime());
        verify(businessNotifyService).sendToUser(eq(10L), any(),
                eq(Map.of("homeworkName", "任务1", "deadline", EduHomeworkRemindServiceImpl.NO_DEADLINE_TEXT)), any());
    }

    @Test
    void remindUnsubmitted_nobodyToRemindDoesNotUseTheInterval() {
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1L, NOW.minusHours(1)));
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L, 11L));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L, 11L, 1)));

        EduHomeworkRemindRespVO resp = asTeacher(() -> service.remindUnsubmitted(1L));

        assertEquals(0, resp.getTargetCount());
        assertEquals(0, resp.getNotifiedCount());
        verifyNoInteractions(notifyStateMapper, businessNotifyService);
    }

    @Test
    void remindUnsubmitted_allSendsFailedReleasesTheInterval() {
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1L, NOW.plusDays(1)));
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(remindPolicy.manualRemindIntervalMinutes()).thenReturn(60);
        EduHomeworkNotifyStateDO state = new EduHomeworkNotifyStateDO();
        state.setManualRemindTime(NOW.minusHours(3).withNano(0));
        when(notifyStateMapper.selectByHomeworkId(1L)).thenReturn(state);
        when(notifyStateMapper.claimManualRemind(eq(1L), any(), any())).thenReturn(1);
        when(businessNotifyService.sendToUser(any(), any(), any(), any())).thenReturn(false);

        EduHomeworkRemindRespVO resp = asTeacher(() -> service.remindUnsubmitted(1L));

        assertEquals(0, resp.getNotifiedCount());
        assertNull(resp.getNextAvailableTime());
        verify(notifyStateMapper).releaseManualRemind(1L, NOW.withNano(0), NOW.minusHours(3).withNano(0));
    }

    @Test
    void remindUnsubmitted_otherTeacherCannotRemind() {
        EduHomeworkDO homework = homework(1L, NOW.plusDays(1));
        homework.setTeacherId(99L);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);

        assertServiceException(() -> asTeacher(() -> service.remindUnsubmitted(1L)), HOMEWORK_NOT_EXISTS);
        verifyNoInteractions(businessNotifyService, notifyStateMapper);
    }

    @Test
    void remindUnsubmitted_taskThatCannotBeSubmittedIsRejected() {
        EduHomeworkDO homework = homework(1L, NOW.plusDays(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(archiveAccessService.canSubmit(homework)).thenReturn(false);

        assertServiceException(() -> asTeacher(() -> service.remindUnsubmitted(1L)), HOMEWORK_REMIND_STATUS_INVALID);
    }

    // ========== 教师新提交汇总 ==========

    @Test
    void sendTeacherDigests_notifiesEachTaskOnceWithCounts() {
        when(remindPolicy.teacherDigestMinutes()).thenReturn(60);
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 10, 9, 58);
        LocalDateTime since = cutoff.minusMinutes(60);
        LocalDateTime gate = NOW.withNano(0).minusMinutes(60);
        LocalDateTime previous = LocalDateTime.of(2026, 10, 10, 8, 40);
        when(notifyStateMapper.selectPendingDigests(cutoff, gate))
                .thenReturn(List.of(digest(1L, since, 3, 1), digest(2L, previous, 2, 0)));
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1L, null));
        when(homeworkMapper.selectById(2L)).thenReturn(homework(2L, null));
        when(notifyStateMapper.advanceDigest(1L, since, cutoff, NOW.withNano(0), gate)).thenReturn(1);
        when(notifyStateMapper.advanceDigest(2L, previous, cutoff, NOW.withNano(0), gate)).thenReturn(1);
        when(businessNotifyService.sendToUser(eq(TEACHER_ID), eq(EduBusinessNotifyService.TEACHER_HOMEWORK_SUBMITTED),
                any(), any())).thenReturn(true);

        assertEquals("教师新提交汇总：通知 2 个任务", service.sendTeacherDigests());

        var ordered = inOrder(notifyStateMapper);
        ordered.verify(notifyStateMapper).initializeDigestCursors(since);
        ordered.verify(notifyStateMapper).advanceEmptyDigestCursors(cutoff);
        ordered.verify(notifyStateMapper).selectPendingDigests(cutoff, gate);
        verify(businessNotifyService).sendToUser(TEACHER_ID, EduBusinessNotifyService.TEACHER_HOMEWORK_SUBMITTED,
                Map.of("homeworkName", "任务1", "count", 3L, "resubmitNote", "（含 1 份重交）"),
                "/capacity/homework_manager/homework/correct/1");
        verify(businessNotifyService).sendToUser(TEACHER_ID, EduBusinessNotifyService.TEACHER_HOMEWORK_SUBMITTED,
                Map.of("homeworkName", "任务2", "count", 2L, "resubmitNote", ""),
                "/capacity/homework_manager/homework/correct/2");
    }

    @Test
    void sendTeacherDigests_lostClaimDoesNotNotifyTwice() {
        when(remindPolicy.teacherDigestMinutes()).thenReturn(30);
        LocalDateTime previous = LocalDateTime.of(2026, 10, 10, 9, 0);
        when(notifyStateMapper.selectPendingDigests(any(), any())).thenReturn(List.of(digest(2L, previous, 4, 2)));
        when(homeworkMapper.selectById(2L)).thenReturn(homework(2L, null));
        when(notifyStateMapper.advanceDigest(eq(2L), eq(previous), any(), any(), any())).thenReturn(0);

        assertEquals("教师新提交汇总：通知 0 个任务", service.sendTeacherDigests());

        verifyNoInteractions(businessNotifyService);
    }

    @Test
    void sendTeacherDigests_emptyFirstScanPersistsCursorForFiveMinuteInterval() {
        when(remindPolicy.teacherDigestMinutes()).thenReturn(5);
        LocalDateTime firstCutoff = NOW.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).minusMinutes(2);
        when(notifyStateMapper.selectPendingDigests(any(), any())).thenReturn(List.of());

        service.sendTeacherDigests();

        verify(notifyStateMapper).initializeDigestCursors(firstCutoff.minusMinutes(5));
        verify(notifyStateMapper).advanceEmptyDigestCursors(firstCutoff);
        verify(notifyStateMapper).advanceGlobalDigestCursor(firstCutoff);
        verifyNoInteractions(businessNotifyService);

        // 首轮没有提交，10:00 的首次提交仍应从 09:58 游标起算，而不是第二轮的 10:03。
        LocalDateTime later = NOW.plusMinutes(10).withNano(0);
        LocalDateTime laterCutoff = firstCutoff.plusMinutes(10);
        service.setClock(Clock.fixed(later.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(notifyStateMapper.selectGlobalDigestCursor()).thenReturn(firstCutoff);
        when(notifyStateMapper.selectPendingDigests(laterCutoff, later.minusMinutes(5)))
                .thenReturn(List.of(digest(1L, firstCutoff, 1, 0)));
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1L, null));
        when(notifyStateMapper.advanceDigest(1L, firstCutoff, laterCutoff, later, later.minusMinutes(5)))
                .thenReturn(1);
        when(businessNotifyService.sendToUser(any(), any(), any(), any())).thenReturn(true);

        assertEquals("教师新提交汇总：通知 1 个任务", service.sendTeacherDigests());
        verify(notifyStateMapper).advanceDigest(1L, firstCutoff, laterCutoff, later, later.minusMinutes(5));
        verify(notifyStateMapper).initializeDigestCursors(firstCutoff);
    }

    @Test
    void sendTeacherDigests_zeroMinutesTurnsDigestOff() {
        when(remindPolicy.teacherDigestMinutes()).thenReturn(0);

        assertEquals("教师新提交汇总已关闭", service.sendTeacherDigests());

        verifyNoInteractions(notifyStateMapper, businessNotifyService);
    }

    // ========== 工具 ==========

    private void prepareSingleDueStudent() {
        when(remindPolicy.remindEnabled()).thenReturn(true);
        when(remindPolicy.remindBeforeHours()).thenReturn(24);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(homework(1L, NOW.plusHours(5))));
        when(extensionMapper.selectListByDeadlineBetween(any(), any())).thenReturn(List.of());
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(1L, 10L));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(remindLogMapper.selectListByHomeworkIdAndType(1L, EduHomeworkRemindLogDO.TYPE_AUTO)).thenReturn(List.of());
    }

    private static <T> T asTeacher(java.util.function.Supplier<T> action) {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(TEACHER_ID);
            return action.get();
        }
    }

    private static EduHomeworkDO homework(Long id, LocalDateTime deadline) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(id);
        homework.setHomeworkName("任务" + id);
        homework.setTeacherId(TEACHER_ID);
        homework.setStatus(1);
        homework.setDeadline(deadline);
        return homework;
    }

    private static List<EduHomeworkAssigneeDO> assignees(Long homeworkId, Long... studentIds) {
        return Arrays.stream(studentIds).map(studentId -> {
            EduHomeworkAssigneeDO assignee = new EduHomeworkAssigneeDO();
            assignee.setHomeworkId(homeworkId);
            assignee.setStudentId(studentId);
            return assignee;
        }).toList();
    }

    private static EduHomeworkRecordDO record(Long homeworkId, Long studentId, int status) {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setHomeworkId(homeworkId);
        record.setStudentId(studentId);
        record.setStatus(status);
        return record;
    }

    private static EduHomeworkExtensionDO extension(Long homeworkId, Long studentId, LocalDateTime deadline) {
        EduHomeworkExtensionDO extension = new EduHomeworkExtensionDO();
        extension.setHomeworkId(homeworkId);
        extension.setStudentId(studentId);
        extension.setDeadline(deadline);
        return extension;
    }

    private static EduHomeworkRemindLogDO remindLog(Long homeworkId, Long studentId) {
        EduHomeworkRemindLogDO remindLog = new EduHomeworkRemindLogDO();
        remindLog.setHomeworkId(homeworkId);
        remindLog.setStudentId(studentId);
        remindLog.setRemindType(EduHomeworkRemindLogDO.TYPE_AUTO);
        return remindLog;
    }

    private static DigestRow digest(Long homeworkId, LocalDateTime digestUntil, long submitted, long resubmitted) {
        DigestRow row = new DigestRow();
        row.setHomeworkId(homeworkId);
        row.setDigestUntil(digestUntil);
        row.setSubmittedCount(submitted);
        row.setResubmittedCount(resubmitted);
        return row;
    }

}
