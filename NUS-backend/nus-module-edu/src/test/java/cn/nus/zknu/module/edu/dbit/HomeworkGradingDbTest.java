package cn.nus.zknu.module.edu.dbit;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordCorrectReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordGradeRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordUpdateReqVO;
import cn.nus.zknu.module.edu.dbit.support.EduDbIntegrationTest;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkGradingActionService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_GRADING_UNDO_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_GRADING_UNDO_EXPIRED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_STATE_CHANGED;
import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.NOTIFY_PENDING;
import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.NOTIFY_SENT;
import static cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum.NOTIFY_UNDONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批改、打回的并发控制与撤销（真实事务、行锁、datetime 精度）。
 */
class HomeworkGradingDbTest extends EduDbIntegrationTest {

    private static final int SCORE = 88;
    private static final String COMMENT = "思路清楚";
    private static final ThreadLocal<Boolean> WAIT_AFTER_READ = new ThreadLocal<>();

    @Autowired
    private EduHomeworkRecordService recordService;
    @Autowired
    private EduHomeworkGradingActionService gradingActionService;

    @BeforeEach
    void notifySucceeds() {
        when(businessNotifyService.sendToUser(anyLong(), anyString(), any(), anyString())).thenReturn(true);
    }

    // ========== b. 同一份提交，批改与打回同时到达 ==========

    @Test
    void concurrentCorrectAndRedo_exactlyOneWins_otherGetsStateChanged() throws Exception {
        // 每种先后各跑两轮：0 = 打回先写，1 = 批改先写，2 = 不干预、自由竞争
        for (int round = 0; round < 6; round++) {
            int mode = round % 3;
            Fixture f = submittedFixture();
            // 学生半小时前提交，两位教师都读到同一个版本
            LocalDateTime readVersion = ageRecord(f.recordId, 30);
            Rendezvous bothRead = new Rendezvous(2);
            doAnswer(invocation -> {
                bothRead.arrive();
                if (Boolean.TRUE.equals(WAIT_AFTER_READ.get())) {
                    Thread.sleep(150); // 让另一方先写入
                }
                return invocation.callRealMethod();
            }).when(aiReviewService).getLatestForCorrection(f.recordId);

            Callable<EduHomeworkRecordGradeRespVO> correct = () -> {
                loginAs(f.teacherId);
                WAIT_AFTER_READ.set(mode == 0);
                try {
                    return recordService.correctRecord(correctReq(f.recordId, readVersion));
                } finally {
                    WAIT_AFTER_READ.remove();
                }
            };
            Callable<EduHomeworkRecordGradeRespVO> redo = () -> {
                loginAs(f.teacherId);
                WAIT_AFTER_READ.set(mode == 1);
                try {
                    return recordService.redoRecord(f.recordId, LocalDateTime.now().plusDays(2), readVersion);
                } finally {
                    WAIT_AFTER_READ.remove();
                }
            };
            List<Outcome<EduHomeworkRecordGradeRespVO>> outcomes = runConcurrently(List.of(correct, redo));
            Outcome<EduHomeworkRecordGradeRespVO> correctOutcome = outcomes.get(0);
            Outcome<EduHomeworkRecordGradeRespVO> redoOutcome = outcomes.get(1);
            final int currentRound = round;

            assertEquals(1, outcomes.stream().filter(Outcome::ok).count(),
                    () -> "第 " + currentRound + " 轮：只能有一方成功，实际 " + outcomes);
            boolean correctWon = correctOutcome.ok();
            if (mode != 2) {
                assertEquals(mode == 1, correctWon, "先写入的一方应成功");
            }
            assertServiceError(HOMEWORK_RECORD_STATE_CHANGED, correctWon ? redoOutcome.error() : correctOutcome.error());
            assertConsistentAfterRace(f, correctWon);
        }
    }

    /** 记录、轨迹、通知队列三者一致：只反映胜者那一次操作。 */
    private void assertConsistentAfterRace(Fixture f, boolean correctWon) {
        Map<String, Object> record = jdbc.queryForMap("SELECT status, score, redo_count, update_time "
                + "FROM edu_homework_record WHERE id = ?", f.recordId);
        int expectedStatus = correctWon ? EduHomeworkRecordStatusEnum.CORRECTED : EduHomeworkRecordStatusEnum.REDO;
        assertEquals(expectedStatus, ((Number) record.get("status")).intValue());
        assertEquals(correctWon ? (Object) SCORE : null,
                record.get("score") == null ? null : ((Number) record.get("score")).intValue());
        assertEquals(correctWon ? 0 : 1, ((Number) record.get("redo_count")).intValue());

        // 轨迹：提交 1 条 + 胜者 1 条；败者回滚后不留痕迹
        assertEquals(List.of("SUBMIT", correctWon ? "CORRECT" : "REDO"), historyEvents(f.recordId));
        // 通知队列：只有胜者的一条待发送，撤销校验用的 after_update_time 与记录一致
        List<Map<String, Object>> actions = jdbc.queryForList("SELECT action_type, notify_status, prev_status, "
                + "after_status, after_update_time FROM edu_homework_grading_action WHERE record_id = ?", f.recordId);
        assertEquals(1, actions.size(), "通知队列里只能有胜者的一条");
        Map<String, Object> action = actions.get(0);
        assertEquals(correctWon ? "CORRECT" : "REDO", action.get("action_type"));
        assertEquals(NOTIFY_PENDING, ((Number) action.get("notify_status")).intValue());
        assertEquals(EduHomeworkRecordStatusEnum.SUBMITTED, ((Number) action.get("prev_status")).intValue());
        assertEquals(expectedStatus, ((Number) action.get("after_status")).intValue());
        assertEquals(record.get("update_time"), action.get("after_update_time"));
    }

    /** 同一秒内写入也推进毫秒版本，读到旧版本的打回必须失败。 */
    @Test
    void concurrentCorrectThenRedo_withinSameSecondAsReadVersion_shouldStillRejectRedo() throws Exception {
        Fixture f = submittedFixture();
        // 固定两个请求的业务时钟，避免机器负载使测试跨秒；两者仍使用真实事务和条件 UPDATE。
        LocalDateTime second = LocalDateTime.now().withNano(0);
        LocalDateTime correctTime = second.plusNanos(200_000_000);
        LocalDateTime redoTime = second.plusNanos(400_000_000);
        jdbc.update("UPDATE edu_homework_record SET update_time = ? WHERE id = ?", second, f.recordId);
        Rendezvous bothRead = new Rendezvous(2);
        CountDownLatch correctCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            bothRead.arrive();
            if (Boolean.TRUE.equals(WAIT_AFTER_READ.get()) && !correctCommitted.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("批改没有在 20 秒内提交");
            }
            return invocation.callRealMethod();
        }).when(aiReviewService).getLatestForCorrection(f.recordId);

        Callable<EduHomeworkRecordGradeRespVO> correct = () -> {
            loginAs(f.teacherId);
            try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                clock.when(LocalDateTime::now).thenReturn(correctTime);
                return recordService.correctRecord(correctReq(f.recordId, second));
            } finally {
                correctCommitted.countDown();
            }
        };
        Callable<EduHomeworkRecordGradeRespVO> redo = () -> {
            loginAs(f.teacherId);
            WAIT_AFTER_READ.set(true);
            try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                clock.when(LocalDateTime::now).thenReturn(redoTime);
                return recordService.redoRecord(f.recordId, second.plusDays(2), second);
            } finally {
                WAIT_AFTER_READ.remove();
            }
        };
        List<Outcome<EduHomeworkRecordGradeRespVO>> outcomes = runConcurrently(List.of(correct, redo));
        assertTrue(outcomes.get(0).ok(), "批改先写入，应成功");
        assertEquals(correctTime, outcomes.get(0).value().getUpdateTime());
        assertEquals(correctTime, jdbc.queryForObject(
                "SELECT update_time FROM edu_homework_record WHERE id = ?", LocalDateTime.class, f.recordId));
        assertServiceError(HOMEWORK_RECORD_STATE_CHANGED, outcomes.get(1).error());
        assertConsistentAfterRace(f, true);
    }

    // ========== d. 撤销 ==========

    @Test
    void undoWithinWindow_restoresRecord_andCancelsQueuedNotification() {
        Fixture f = submittedFixture();
        ageRecord(f.recordId, 30);
        loginAs(f.teacherId);

        EduHomeworkRecordGradeRespVO graded = recordService.correctRecord(correctReq(f.recordId, null));
        assertNotNull(graded.getUndoToken(), "批改后应返回撤销凭据");
        assertTrue(graded.getUndoSeconds() > 0);
        // 返回给前端的 updateTime 与库里一致（下一次乐观校验用）
        assertEquals(graded.getUpdateTime(), jdbc.queryForObject(
                "SELECT update_time FROM edu_homework_record WHERE id = ?", LocalDateTime.class, f.recordId));

        EduHomeworkRecordGradeRespVO undone = recordService.undoGrading(graded.getUndoToken());

        assertEquals(EduHomeworkRecordStatusEnum.SUBMITTED, undone.getStatus());
        Map<String, Object> record = jdbc.queryForMap("SELECT status, score, comment, correct_type, correct_time "
                + "FROM edu_homework_record WHERE id = ?", f.recordId);
        assertEquals(EduHomeworkRecordStatusEnum.SUBMITTED, ((Number) record.get("status")).intValue());
        assertNull(record.get("score"));
        assertNull(record.get("comment"));
        assertNull(record.get("correct_type"));
        assertNull(record.get("correct_time"));
        Map<String, Object> action = jdbc.queryForMap("SELECT notify_status, notify_time FROM "
                + "edu_homework_grading_action WHERE id = ?", graded.getUndoToken());
        assertEquals(NOTIFY_UNDONE, ((Number) action.get("notify_status")).intValue());
        assertNotNull(action.get("notify_time"));
        assertEquals(List.of("SUBMIT", "CORRECT", "UNDO"), historyEvents(f.recordId));

        // 撤销期过后发送任务也不会再发这条
        makeDue(graded.getUndoToken());
        gradingActionService.sendDueNotifications();
        verify(businessNotifyService, never()).sendToUser(eq(f.studentId), anyString(), any(), anyString());
        assertEquals(NOTIFY_UNDONE, notifyStatus(graded.getUndoToken()));
    }

    @Test
    void undoAfterWindow_fails_andNotificationIsSentOnce() {
        Fixture f = submittedFixture();
        ageRecord(f.recordId, 30);
        loginAs(f.teacherId);
        EduHomeworkRecordGradeRespVO graded = recordService.correctRecord(correctReq(f.recordId, null));
        makeDue(graded.getUndoToken());

        assertServiceError(HOMEWORK_GRADING_UNDO_EXPIRED,
                assertThrows(RuntimeException.class, () -> recordService.undoGrading(graded.getUndoToken())));

        assertEquals(EduHomeworkRecordStatusEnum.CORRECTED, recordStatus(f.recordId));
        assertEquals(NOTIFY_PENDING, notifyStatus(graded.getUndoToken()));
        gradingActionService.sendDueNotifications();
        gradingActionService.sendDueNotifications(); // 重复调度不重发
        verify(businessNotifyService, times(1)).sendToUser(eq(f.studentId),
                eq(EduBusinessNotifyService.HOMEWORK_CORRECTED), any(), eq("/student/homework/feedback/" + f.homeworkId));
        assertEquals(NOTIFY_SENT, notifyStatus(graded.getUndoToken()));
    }

    @Test
    void undoRedo_restoresStatusAndRedoCount() {
        Fixture f = submittedFixture();
        ageRecord(f.recordId, 30);
        loginAs(f.teacherId);
        EduHomeworkRecordGradeRespVO redone = recordService.redoRecord(f.recordId,
                LocalDateTime.now().plusDays(2), null);
        assertEquals(EduHomeworkRecordStatusEnum.REDO, recordStatus(f.recordId));

        recordService.undoGrading(redone.getUndoToken());

        Map<String, Object> record = jdbc.queryForMap("SELECT status, redo_count, redo_deadline "
                + "FROM edu_homework_record WHERE id = ?", f.recordId);
        assertEquals(EduHomeworkRecordStatusEnum.SUBMITTED, ((Number) record.get("status")).intValue());
        assertEquals(0, ((Number) record.get("redo_count")).intValue());
        assertNull(record.get("redo_deadline"));
        assertEquals(NOTIFY_UNDONE, notifyStatus(redone.getUndoToken()));
    }

    @Test
    void undoAfterStudentResubmitted_fails_andQueuedNotificationSurvivesRollback() {
        Fixture f = submittedFixture();
        ageRecord(f.recordId, 30);
        loginAs(f.teacherId);
        EduHomeworkRecordGradeRespVO redone = recordService.redoRecord(f.recordId,
                LocalDateTime.now().plusDays(2), null);
        // 撤销期内学生已经重交
        EduHomeworkRecordUpdateReqVO resubmit = new EduHomeworkRecordUpdateReqVO();
        resubmit.setId(f.recordId);
        resubmit.setContent("改好了");
        recordService.updateRecord(resubmit, f.studentId);

        assertServiceError(HOMEWORK_GRADING_UNDO_CHANGED,
                assertThrows(RuntimeException.class, () -> recordService.undoGrading(redone.getUndoToken())));

        assertEquals(EduHomeworkRecordStatusEnum.RESUBMITTED, recordStatus(f.recordId));
        // 撤销先抢占了通知，但随事务回滚，打回通知仍会发出
        assertEquals(NOTIFY_PENDING, notifyStatus(redone.getUndoToken()));
        assertEquals(List.of("SUBMIT", "REDO", "RESUBMIT"), historyEvents(f.recordId));
    }

    // ========== 工具 ==========

    /** 新任务 + 学生已提交（走真实 Service）。 */
    private Fixture submittedFixture() {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long homeworkId = homework(scope).create();
        long studentId = newUserId();
        joinClass(scope, studentId);
        assign(homeworkId, scope, studentId);
        EduHomeworkRecordSubmitReqVO req = new EduHomeworkRecordSubmitReqVO();
        req.setHomeworkId(homeworkId);
        req.setContent("作答");
        Long recordId = recordService.submitRecord(req, studentId);
        return new Fixture(teacherId, homeworkId, studentId, recordId);
    }

    private static EduHomeworkRecordCorrectReqVO correctReq(long recordId, LocalDateTime expectedUpdateTime) {
        EduHomeworkRecordCorrectReqVO req = new EduHomeworkRecordCorrectReqVO();
        req.setId(recordId);
        req.setScore(SCORE);
        req.setComment(COMMENT);
        req.setExpectedUpdateTime(expectedUpdateTime);
        return req;
    }

    private void makeDue(Long actionId) {
        jdbc.update("UPDATE edu_homework_grading_action SET undo_deadline = ? WHERE id = ?",
                LocalDateTime.now().withNano(0).minusMinutes(1), actionId);
    }

    private int notifyStatus(Long actionId) {
        return jdbc.queryForObject("SELECT notify_status FROM edu_homework_grading_action WHERE id = ?",
                Integer.class, actionId);
    }

    private int recordStatus(long recordId) {
        return jdbc.queryForObject("SELECT status FROM edu_homework_record WHERE id = ?", Integer.class, recordId);
    }

    private List<String> historyEvents(long recordId) {
        return jdbc.queryForList("SELECT event_type FROM edu_homework_record_history WHERE record_id = ? "
                + "AND deleted = 0 ORDER BY id", String.class, recordId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private record Fixture(long teacherId, long homeworkId, long studentId, long recordId) {
    }
}
