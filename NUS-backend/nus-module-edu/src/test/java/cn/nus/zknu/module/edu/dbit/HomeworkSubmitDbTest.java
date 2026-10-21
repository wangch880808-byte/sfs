package cn.nus.zknu.module.edu.dbit;

import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionSetReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordUpdateReqVO;
import cn.nus.zknu.module.edu.dbit.support.EduDbIntegrationTest;
import cn.nus.zknu.module.edu.dbit.support.RecordingExecutor;
import cn.nus.zknu.module.edu.service.aireview.HomeworkAiReviewDispatcher;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkExtensionService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_ALREADY_SUBMITTED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_DEADLINE_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 学生提交：AI 初评排队与事务提交后派发、重复提交、个人延期。
 */
class HomeworkSubmitDbTest extends EduDbIntegrationTest {

    @Autowired
    private EduHomeworkRecordService recordService;
    @Autowired
    private EduHomeworkExtensionService extensionService;
    @Autowired
    private HomeworkAiReviewDispatcher dispatcher;

    // ========== a. AI 初评：排队记录随提交事务写入，事务提交后才派发 ==========

    @Test
    void submit_commitsQueuedReview_andDispatchesOnlyAfterCommit() {
        Fixture f = fixture(true, LocalDateTime.now().plusDays(1));

        Long recordId = recordService.submitRecord(submitReq(f.homeworkId), f.studentId);

        Long reviewId = jdbc.queryForObject(
                "SELECT id FROM edu_homework_ai_review WHERE homework_record_id = ? AND deleted = 0",
                Long.class, recordId);
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, reviewStatus(reviewId));
        List<RecordingExecutor.Dispatch> dispatches = aiReviewExecutor.dispatches();
        assertEquals(1, dispatches.size(), "提交后应派发一次");
        // 派发时用另一条连接已能读到排队记录：派发发生在提交之后
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, dispatches.get(0).committedReviewStatuses().get(reviewId),
                "派发时排队记录必须已提交");
    }

    @Test
    void submitInsideOuterTransaction_dispatchWaitsForOuterCommit() {
        Fixture f = fixture(true, LocalDateTime.now().plusDays(1));

        Long recordId = tx.execute(status -> {
            Long id = recordService.submitRecord(submitReq(f.homeworkId), f.studentId);
            assertTrue(aiReviewExecutor.dispatches().isEmpty(), "外层事务未提交前不能派发");
            // 另一条连接此时还看不到提交记录和排队记录
            assertEquals(0, countFromAnotherConnection(
                    "SELECT COUNT(*) FROM edu_homework_ai_review WHERE homework_record_id = " + id));
            assertEquals(0, countFromAnotherConnection(
                    "SELECT COUNT(*) FROM edu_homework_record WHERE id = " + id));
            return id;
        });

        Long reviewId = jdbc.queryForObject(
                "SELECT id FROM edu_homework_ai_review WHERE homework_record_id = ?", Long.class, recordId);
        List<RecordingExecutor.Dispatch> dispatches = aiReviewExecutor.dispatches();
        assertEquals(1, dispatches.size());
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, dispatches.get(0).committedReviewStatuses().get(reviewId));
    }

    @Test
    void submitRolledBack_leavesNoRecordNoReview_andNeverDispatches() {
        Fixture f = fixture(true, LocalDateTime.now().plusDays(1));

        tx.executeWithoutResult(status -> {
            recordService.submitRecord(submitReq(f.homeworkId), f.studentId);
            status.setRollbackOnly(); // 例如同一事务里后续步骤失败
        });

        assertEquals(0, count("SELECT COUNT(*) FROM edu_homework_record WHERE homework_id = ?", f.homeworkId));
        assertEquals(0, count("SELECT COUNT(*) FROM edu_homework_ai_review WHERE homework_id = ?", f.homeworkId));
        assertEquals(0, count("SELECT COUNT(*) FROM edu_homework_record_history WHERE homework_id = ?", f.homeworkId));
        assertTrue(aiReviewExecutor.dispatches().isEmpty(), "回滚后不应派发");

        // 回滚后学生可以重新提交，且这次正常排队、派发
        Long recordId = recordService.submitRecord(submitReq(f.homeworkId), f.studentId);
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_ai_review WHERE homework_record_id = ?", recordId));
        assertEquals(1, aiReviewExecutor.dispatches().size());
    }

    @Test
    void resubmitAfterRedo_queuesFreshReviewForNewVersion_andDispatchesAfterCommit() {
        Fixture f = fixture(true, LocalDateTime.now().plusDays(1));
        Long recordId = recordService.submitRecord(submitReq(f.homeworkId), f.studentId);
        Long firstReview = jdbc.queryForObject("SELECT id FROM edu_homework_ai_review WHERE homework_record_id = ?",
                Long.class, recordId);
        jdbc.update("UPDATE edu_homework_ai_review SET status = ? WHERE id = ?",
                HomeworkAiReviewStatusEnum.READY, firstReview); // 模拟初评已完成
        ageRecord(recordId, 30);
        loginAs(f.scope.teacherId());
        recordService.redoRecord(recordId, LocalDateTime.now().plusDays(2), null);
        assertEquals(HomeworkAiReviewStatusEnum.REJECTED, reviewStatus(firstReview), "打回时驳回旧草案");
        aiReviewExecutor.clear();

        EduHomeworkRecordUpdateReqVO resubmit = new EduHomeworkRecordUpdateReqVO();
        resubmit.setId(recordId);
        resubmit.setContent("重交的作答");
        recordService.updateRecord(resubmit, f.studentId);

        Map<String, Object> latest = jdbc.queryForMap("SELECT r.id, r.status, r.source_submit_time, "
                + "hr.submit_time FROM edu_homework_ai_review r JOIN edu_homework_record hr "
                + "ON hr.id = r.homework_record_id WHERE r.homework_record_id = ? ORDER BY r.id DESC LIMIT 1", recordId);
        Long secondReview = ((Number) latest.get("id")).longValue();
        assertTrue(secondReview > firstReview);
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, ((Number) latest.get("status")).intValue());
        assertEquals(latest.get("submit_time"), latest.get("source_submit_time"), "新草案对应重交后的版本");
        List<RecordingExecutor.Dispatch> dispatches = aiReviewExecutor.dispatches();
        assertEquals(1, dispatches.size());
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, dispatches.get(0).committedReviewStatuses().get(secondReview));
    }

    /** 检测手段自检：如果在事务提交前就派发，上面几条用例拿到的快照里不会有这条排队记录。 */
    @Test
    void detectorSelfCheck_dispatchBeforeCommitIsVisibleAsMissingRow() {
        Fixture f = fixture(true, LocalDateTime.now().plusDays(1));
        Long reviewId = tx.execute(status -> {
            jdbc.update("INSERT INTO edu_homework_ai_review (homework_record_id, homework_id, student_id, status, "
                    + "submission_mode, generation_token) VALUES (0, ?, ?, 0, 'text', ?)",
                    f.homeworkId, f.studentId, java.util.UUID.randomUUID().toString());
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            dispatcher.dispatch(id); // 故意在提交前派发
            return id;
        });
        List<RecordingExecutor.Dispatch> dispatches = aiReviewExecutor.dispatches();
        assertEquals(1, dispatches.size());
        assertFalse(dispatches.get(0).committedReviewStatuses().containsKey(reviewId),
                "提交前派发时，新连接不应读到排队记录");
    }

    @Test
    void submitWithoutAutoGrade_doesNotQueueReview() {
        Fixture f = fixture(false, LocalDateTime.now().plusDays(1));

        Long recordId = recordService.submitRecord(submitReq(f.homeworkId), f.studentId);

        assertEquals(0, count("SELECT COUNT(*) FROM edu_homework_ai_review WHERE homework_record_id = ?", recordId));
        assertTrue(aiReviewExecutor.dispatches().isEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_record_history WHERE record_id = ? "
                + "AND event_type = 'SUBMIT'", recordId));
    }

    // ========== c. 重复提交 ==========

    @Test
    void secondSubmitBySameStudent_isRejectedAsAlreadySubmitted() {
        Fixture f = fixture(false, LocalDateTime.now().plusDays(1));
        recordService.submitRecord(submitReq(f.homeworkId), f.studentId);

        Throwable error = assertThrows(RuntimeException.class,
                () -> recordService.submitRecord(submitReq(f.homeworkId), f.studentId));

        assertServiceError(HOMEWORK_RECORD_ALREADY_SUBMITTED, error);
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_record WHERE homework_id = ? AND student_id = ?",
                f.homeworkId, f.studentId));
    }

    @Test
    void concurrentDuplicateSubmit_uniqueKeyRejectsLoser_withFriendlyError() throws Exception {
        Fixture f = fixture(true, LocalDateTime.now().plusDays(1));
        // 两个请求都通过“是否已提交”的查询后再一起写入，后写的一方只能被唯一键挡住
        Rendezvous bothChecked = new Rendezvous(2);
        doAnswer(invocation -> {
            bothChecked.arrive();
            return null;
        }).when(homeworkAttachmentService).validateAndNormalize(any(), anyLong(), any(), any());

        Callable<Long> submit = () -> recordService.submitRecord(submitReq(f.homeworkId), f.studentId);
        List<Outcome<Long>> outcomes = runConcurrently(List.of(submit, submit));

        verify(homeworkAttachmentService, times(2)).validateAndNormalize(any(), anyLong(), any(), any());
        List<Outcome<Long>> winners = outcomes.stream().filter(Outcome::ok).toList();
        List<Outcome<Long>> losers = outcomes.stream().filter(outcome -> !outcome.ok()).toList();
        assertEquals(1, winners.size(), "只能有一份提交成功");
        assertEquals(1, losers.size());
        assertServiceError(HOMEWORK_RECORD_ALREADY_SUBMITTED, losers.get(0).error());

        Long recordId = winners.get(0).value();
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_record WHERE homework_id = ? AND student_id = ?",
                f.homeworkId, f.studentId));
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_record_history WHERE homework_id = ?", f.homeworkId));
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_ai_review WHERE homework_id = ?", f.homeworkId));
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_ai_review WHERE homework_record_id = ?", recordId));
        assertEquals(1, aiReviewExecutor.dispatches().size(), "失败的一方不应派发");
    }

    // ========== f. 个人延期 ==========

    @Test
    void afterTaskDeadline_submitFailsWithoutExtension_andSucceedsWithinPersonalExtension() {
        Fixture f = fixture(false, LocalDateTime.now().minusHours(1));
        long otherStudent = newUserId();
        joinClass(f.scope, otherStudent);
        assign(f.homeworkId, f.scope, otherStudent);

        // 没有延期：已过截止
        assertServiceError(HOMEWORK_RECORD_DEADLINE_EXCEEDED, assertThrows(RuntimeException.class,
                () -> recordService.submitRecord(submitReq(f.homeworkId), f.studentId)));

        // 教师给该学生延期 2 小时（走真实 Service，写 edu_homework_extension）
        loginAs(f.scope.teacherId());
        EduHomeworkExtensionSetReqVO req = new EduHomeworkExtensionSetReqVO();
        req.setHomeworkId(f.homeworkId);
        req.setStudentIds(List.of(f.studentId));
        req.setDeadline(LocalDateTime.now().plusHours(2));
        req.setReason("病假");
        assertEquals(1, extensionService.setExtensions(req));
        verify(businessNotifyService, times(1)).sendToUsers(any(), anyString(), any(), anyString());

        LocalDateTime personal = extensionService.getPersonalDeadline(f.homeworkId, f.studentId);
        assertNotNull(personal);
        assertTrue(personal.isAfter(LocalDateTime.now()));

        Long recordId = assertDoesNotThrow(() -> recordService.submitRecord(submitReq(f.homeworkId), f.studentId));
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_record WHERE id = ? AND status = 1", recordId));
        // 没有延期的同学仍然不能提交
        assertServiceError(HOMEWORK_RECORD_DEADLINE_EXCEEDED, assertThrows(RuntimeException.class,
                () -> recordService.submitRecord(submitReq(f.homeworkId), otherStudent)));
    }

    @Test
    void expiredPersonalExtension_stillBlocksSubmit_andDeletedExtensionCanBeGrantedAgain() {
        Fixture f = fixture(false, LocalDateTime.now().minusHours(3));
        // 个人截止也已过（直接写库：Service 不允许设置过去的时间）
        jdbc.update("INSERT INTO edu_homework_extension (homework_id, student_id, deadline, reason, operator_id) "
                + "VALUES (?, ?, ?, '', ?)", f.homeworkId, f.studentId, LocalDateTime.now().minusMinutes(10),
                f.scope.teacherId());
        assertServiceError(HOMEWORK_RECORD_DEADLINE_EXCEEDED, assertThrows(RuntimeException.class,
                () -> recordService.submitRecord(submitReq(f.homeworkId), f.studentId)));

        // 逻辑删除旧延期后可以重新设置（唯一键只约束未删除的行）
        loginAs(f.scope.teacherId());
        Long extensionId = jdbc.queryForObject("SELECT id FROM edu_homework_extension WHERE homework_id = ? "
                + "AND student_id = ? AND deleted = 0", Long.class, f.homeworkId, f.studentId);
        extensionService.deleteExtension(extensionId);
        EduHomeworkExtensionSetReqVO req = new EduHomeworkExtensionSetReqVO();
        req.setHomeworkId(f.homeworkId);
        req.setStudentIds(List.of(f.studentId));
        req.setDeadline(LocalDateTime.now().plusHours(1));
        extensionService.setExtensions(req);
        assertEquals(2, count("SELECT COUNT(*) FROM edu_homework_extension WHERE homework_id = ? AND student_id = ?",
                f.homeworkId, f.studentId));
        assertEquals(1, count("SELECT COUNT(*) FROM edu_homework_extension WHERE homework_id = ? AND student_id = ? "
                + "AND deleted = 0", f.homeworkId, f.studentId));

        assertDoesNotThrow(() -> recordService.submitRecord(submitReq(f.homeworkId), f.studentId));
    }

    // ========== 工具 ==========

    private Fixture fixture(boolean autoGrade, LocalDateTime deadline) {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long homeworkId = homework(scope).autoGrade(autoGrade).deadline(deadline).create();
        long studentId = newUserId();
        joinClass(scope, studentId);
        assign(homeworkId, scope, studentId);
        return new Fixture(scope, homeworkId, studentId);
    }

    private static EduHomeworkRecordSubmitReqVO submitReq(long homeworkId) {
        EduHomeworkRecordSubmitReqVO req = new EduHomeworkRecordSubmitReqVO();
        req.setHomeworkId(homeworkId);
        req.setContent("我的作答");
        return req;
    }

    private Integer reviewStatus(Long reviewId) {
        return jdbc.queryForObject("SELECT status FROM edu_homework_ai_review WHERE id = ?", Integer.class, reviewId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** 在新线程里查询：不会复用当前线程绑定的事务连接，只能看到已提交的数据。 */
    private long countFromAnotherConnection(String sql) {
        return inAnotherThread(() -> count(sql));
    }

    private record Fixture(Scope scope, long homeworkId, long studentId) {
    }
}
