package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkGradingActionDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkGradingActionMapper;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.aireview.HomeworkAiReviewDispatcher;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EduHomeworkGradingActionServiceTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkAiReviewDO.class);
    }

    @InjectMocks
    private EduHomeworkGradingActionService service;
    @Mock
    private EduHomeworkGradingActionMapper actionMapper;
    @Mock
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Mock
    private EduGradingPolicy gradingPolicy;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private HomeworkAiReviewDispatcher aiReviewDispatcher;

    @Test
    void record_snapshotsPreviousStateAndQueuesNotificationAfterUndoWindow() {
        when(gradingPolicy.undoSeconds()).thenReturn(10);
        EduHomeworkRecordDO before = new EduHomeworkRecordDO();
        before.setId(1L);
        before.setHomeworkId(100L);
        before.setStudentId(10L);
        before.setStatus(1);
        before.setScore(70);
        before.setComment("旧评语");
        before.setRedoCount(2);
        before.setRedoDeadline(LocalDateTime.of(2026, 10, 1, 23, 59, 59));
        EduHomeworkAiReviewDO ai = new EduHomeworkAiReviewDO();
        ai.setId(9L);
        ai.setStatus(2);
        LocalDateTime after = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);

        LocalDateTime start = LocalDateTime.now();
        service.record(before, ACTION_CORRECT, 2, after, 31L, 7L, ai,
                EduBusinessNotifyService.HOMEWORK_CORRECTED, Map.of("score", 86), "/student/homework/feedback/100");

        ArgumentCaptor<EduHomeworkGradingActionDO> captor = ArgumentCaptor.forClass(EduHomeworkGradingActionDO.class);
        verify(actionMapper).insert(captor.capture());
        EduHomeworkGradingActionDO action = captor.getValue();
        assertEquals(1L, action.getRecordId());
        assertEquals(1, action.getPrevStatus());
        assertEquals(70, action.getPrevScore());
        assertEquals("旧评语", action.getPrevComment());
        assertEquals(2, action.getPrevRedoCount());
        assertEquals(before.getRedoDeadline(), action.getPrevRedoDeadline());
        assertEquals(2, action.getAfterStatus());
        assertEquals(after, action.getAfterUpdateTime());
        assertEquals(9L, action.getAiReviewId());
        assertEquals(2, action.getAiPrevStatus());
        assertEquals(31L, action.getHistoryId());
        assertEquals(7L, action.getOperatorId());
        assertEquals(NOTIFY_PENDING, action.getNotifyStatus());
        assertTrue(action.getNotifyParams().contains("86"));
        long delay = Duration.between(start.withNano(0), action.getUndoDeadline()).getSeconds();
        assertTrue(delay >= 9 && delay <= 11, "delay=" + delay);
    }

    @Test
    void findRepeat_onlyMatchesSameOperatorPendingAndUnchangedRecord() {
        LocalDateTime after = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        EduHomeworkRecordDO current = new EduHomeworkRecordDO();
        current.setId(1L);
        current.setStatus(2);
        current.setUpdateTime(after);
        EduHomeworkGradingActionDO latest = pending(after);
        when(actionMapper.selectLatestByRecordId(1L)).thenReturn(latest);

        assertSame(latest, service.findRepeat(current, ACTION_CORRECT, 7L));
        assertNull(service.findRepeat(current, ACTION_CORRECT, 8L));
        assertNull(service.findRepeat(current, ACTION_REDO, 7L));
        latest.setNotifyStatus(NOTIFY_SENT);
        assertNull(service.findRepeat(current, ACTION_CORRECT, 7L));
        latest.setNotifyStatus(NOTIFY_PENDING);
        current.setUpdateTime(after.plusNanos(1_000_000));
        assertNull(service.findRepeat(current, ACTION_CORRECT, 7L));
    }

    @Test
    void cancelIfUndoable_respectsDeadlineAndRaceWithSender() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 9, 30, 20);
        EduHomeworkGradingActionDO action = pending(now.minusSeconds(5));
        action.setUndoDeadline(now.plusSeconds(5));

        when(actionMapper.transitFromPending(55L, NOTIFY_UNDONE, now)).thenReturn(1, 0);
        assertTrue(service.cancelIfUndoable(action, now));
        // 发送任务先一步抢到
        assertFalse(service.cancelIfUndoable(action, now));

        // 超过撤销期与宽限期，不再尝试
        action.setUndoDeadline(now.minusSeconds(EduHomeworkGradingActionService.UNDO_GRACE_SECONDS + 1));
        assertFalse(service.cancelIfUndoable(action, now));
        // 已撤销或已发送
        action.setUndoDeadline(now.plusSeconds(5));
        action.setNotifyStatus(NOTIFY_UNDONE);
        assertFalse(service.cancelIfUndoable(action, now));
        verify(actionMapper, times(2)).transitFromPending(any(), anyInt(), any());
    }

    @Test
    void remainingSeconds_isZeroOnceNotPending() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 9, 30, 20);
        EduHomeworkGradingActionDO action = pending(now);
        action.setUndoDeadline(now.plusSeconds(7));
        assertEquals(7, service.remainingSeconds(action, now));
        assertEquals(0, service.remainingSeconds(action, now.plusSeconds(30)));
        action.setNotifyStatus(NOTIFY_UNDONE);
        assertEquals(0, service.remainingSeconds(action, now));
        assertEquals(0, service.remainingSeconds(null, now));
    }

    @Test
    void sendDueNotifications_claimsBeforeSendingAndNeverSendsTwice() {
        EduHomeworkGradingActionDO first = due(1L);
        EduHomeworkGradingActionDO lostRace = due(2L);
        EduHomeworkGradingActionDO failing = due(3L);
        when(actionMapper.selectDue(any(), anyInt())).thenReturn(List.of(first, lostRace, failing));
        when(actionMapper.transitFromPending(eq(1L), eq(NOTIFY_SENT), any())).thenReturn(1);
        when(actionMapper.transitFromPending(eq(2L), eq(NOTIFY_SENT), any())).thenReturn(0);
        when(actionMapper.transitFromPending(eq(3L), eq(NOTIFY_SENT), any())).thenReturn(1);
        when(businessNotifyService.sendToUser(eq(10L), eq(EduBusinessNotifyService.HOMEWORK_CORRECTED),
                argThat(params -> Integer.valueOf(86).equals(params.get("score"))), eq("/student/homework/feedback/100")))
                .thenReturn(true, false);

        assertEquals(1, service.sendDueNotifications());

        verify(businessNotifyService, times(2)).sendToUser(any(), any(), any(), any());
        ArgumentCaptor<EduHomeworkGradingActionDO> failed = ArgumentCaptor.forClass(EduHomeworkGradingActionDO.class);
        verify(actionMapper).updateById(failed.capture());
        assertEquals(3L, failed.getValue().getId());
        assertEquals(NOTIFY_FAILED, failed.getValue().getNotifyStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreAiReview_processingIsRequeuedWithFreshGenerationAndDispatchedAfterCommit() {
        EduHomeworkGradingActionDO action = pending(LocalDateTime.now());
        action.setAiReviewId(9L);
        action.setAiPrevStatus(HomeworkAiReviewStatusEnum.PROCESSING);
        when(aiReviewMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.restoreAiReview(action);

        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkAiReviewDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        var order = inOrder(aiReviewMapper, aiReviewDispatcher);
        order.verify(aiReviewMapper).update(isNull(), captor.capture());
        order.verify(aiReviewDispatcher).dispatchAfterCommit(9L);
        LambdaUpdateWrapper<EduHomeworkAiReviewDO> update = captor.getValue();
        String sql = update.getSqlSet();
        assertTrue(sql.contains("generation_token="));
        assertTrue(sql.contains("update_time="));
        Object restoredStatus = valueSetFor(update, "status");
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, restoredStatus);
        String generation = (String) valueSetFor(update, "generation_token");
        assertNotNull(generation);
        assertDoesNotThrow(() -> java.util.UUID.fromString(generation));
        assertNotNull(valueSetFor(update, "update_time"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreAiReview_lostRaceWithRegenerationDoesNotDispatch() {
        EduHomeworkGradingActionDO action = pending(LocalDateTime.now());
        action.setAiReviewId(9L);
        action.setAiPrevStatus(HomeworkAiReviewStatusEnum.PROCESSING);
        when(aiReviewMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        service.restoreAiReview(action);

        verifyNoInteractions(aiReviewDispatcher);
    }

    private static Object valueSetFor(LambdaUpdateWrapper<EduHomeworkAiReviewDO> update, String column) {
        String assignment = java.util.Arrays.stream(update.getSqlSet().split(","))
                .filter(part -> part.startsWith(column + "=")).findFirst().orElseThrow();
        String key = assignment.substring(assignment.indexOf("MPGENVAL"), assignment.indexOf("}"));
        return update.getParamNameValuePairs().get(key);
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreAiReview_onlyTouchesDecisionLeftByThisAction() {
        EduHomeworkGradingActionDO action = pending(LocalDateTime.now());
        service.restoreAiReview(action);
        verify(aiReviewMapper, never()).update(any(), any(LambdaUpdateWrapper.class));

        action.setAiReviewId(9L);
        action.setAiPrevStatus(2);
        service.restoreAiReview(action);
        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkAiReviewDO>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(aiReviewMapper).update(isNull(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSet().contains("status"));
        assertTrue(wrapper.getValue().getSqlSet().contains("teacher_decision"));
        assertTrue(wrapper.getValue().getSqlSegment().contains("status IN"));
        assertFalse(wrapper.getValue().getSqlSet().contains("generation_token"));
        verifyNoInteractions(aiReviewDispatcher);
    }

    @Test
    void gradingPolicy_fallsBackOnInvalidValues() {
        EduGradingPolicy policy = new EduGradingPolicy();
        ConfigApi configApi = mock(ConfigApi.class);
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "configApi", configApi);
        when(configApi.getConfigValueByKey(EduGradingPolicy.UNDO_SECONDS_KEY))
                .thenReturn(null, "15", "0", "-1", "abc", "1000");
        assertEquals(10, policy.undoSeconds());
        assertEquals(15, policy.undoSeconds());
        assertEquals(0, policy.undoSeconds());
        assertEquals(10, policy.undoSeconds());
        assertEquals(10, policy.undoSeconds());
        assertEquals(10, policy.undoSeconds());
    }

    private static EduHomeworkGradingActionDO pending(LocalDateTime afterUpdateTime) {
        EduHomeworkGradingActionDO action = new EduHomeworkGradingActionDO();
        action.setId(55L);
        action.setRecordId(1L);
        action.setActionType(ACTION_CORRECT);
        action.setOperatorId(7L);
        action.setAfterStatus(2);
        action.setAfterUpdateTime(afterUpdateTime);
        action.setUndoDeadline(afterUpdateTime.plusSeconds(10));
        action.setNotifyStatus(NOTIFY_PENDING);
        return action;
    }

    private static EduHomeworkGradingActionDO due(Long id) {
        EduHomeworkGradingActionDO action = pending(LocalDateTime.now().minusMinutes(1));
        action.setId(id);
        action.setStudentId(10L);
        action.setNotifyTemplate(EduBusinessNotifyService.HOMEWORK_CORRECTED);
        action.setNotifyParams("{\"homeworkName\":\"口语训练\",\"score\":86,\"comment\":\"完成良好\"}");
        action.setNotifyUrl("/student/homework/feedback/100");
        return action;
    }
}
