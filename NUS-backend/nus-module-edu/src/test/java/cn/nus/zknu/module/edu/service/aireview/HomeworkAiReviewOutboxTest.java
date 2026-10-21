package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduReviewRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewProperties;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.support.ReviewEvidence;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 提交 → 排队 → 事务提交后派发 → 认领 → 生成 / 失败 / 跳过 的完整链路，用内存表模拟条件更新。
 */
class HomeworkAiReviewOutboxTest {

    private static final long RECORD_ID = 1L;
    private static final long HOMEWORK_ID = 10L;
    private static final long TEACHER_ID = 7L;

    private final FakeAiReviewTable table = new FakeAiReviewTable();
    private final EduHomeworkRecordMapper recordMapper = mock(EduHomeworkRecordMapper.class);
    private final EduHomeworkMapper homeworkMapper = mock(EduHomeworkMapper.class);
    private final HomeworkReviewEvidenceService evidenceService = mock(HomeworkReviewEvidenceService.class);
    private final AiEduReviewApi aiApi = mock(AiEduReviewApi.class);
    private final AiReviewCalibrationService calibrationService = mock(AiReviewCalibrationService.class);
    private final EduMetricPolicy metricPolicy = mock(EduMetricPolicy.class);
    private final CapacityIndicatorApi indicatorMapper = mock(CapacityIndicatorApi.class);
    private final HomeworkAiReviewFailureNotifier notifier = mock(HomeworkAiReviewFailureNotifier.class);
    private final EduAiReviewPolicy policy = mock(EduAiReviewPolicy.class);
    private final ControllableExecutor executor = new ControllableExecutor();
    private final EduAiReviewProperties properties = new EduAiReviewProperties();

    private final EduHomeworkAiReviewServiceImpl service = new EduHomeworkAiReviewServiceImpl();
    private final HomeworkAiReviewDispatcher dispatcher = new HomeworkAiReviewDispatcher();

    private EduHomeworkRecordDO record;
    private EduHomeworkDO homework;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "aiReviewMapper", table.mapper);
        ReflectionTestUtils.setField(service, "homeworkRecordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "homeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(service, "reviewEvidenceService", evidenceService);
        ReflectionTestUtils.setField(service, "aiEduReviewApi", aiApi);
        ReflectionTestUtils.setField(service, "calibrationService", calibrationService);
        ReflectionTestUtils.setField(service, "metricPolicy", metricPolicy);
        ReflectionTestUtils.setField(service, "capacityIndicatorApi", indicatorMapper);
        ReflectionTestUtils.setField(service, "failureNotifier", notifier);
        ReflectionTestUtils.setField(service, "dispatcher", dispatcher);
        ReflectionTestUtils.setField(dispatcher, "executor", executor);
        ReflectionTestUtils.setField(dispatcher, "reviewMapper", table.mapper);
        ReflectionTestUtils.setField(dispatcher, "reviewService", service);
        ReflectionTestUtils.setField(dispatcher, "policy", policy);
        ReflectionTestUtils.setField(dispatcher, "failureNotifier", notifier);

        when(policy.dispatchBatchSize()).thenReturn(20);
        when(policy.queueTimeoutMinutes()).thenReturn(120);
        when(policy.processingTimeoutSeconds()).thenReturn(2160L);

        record = new EduHomeworkRecordDO();
        record.setId(RECORD_ID);
        record.setHomeworkId(HOMEWORK_ID);
        record.setStudentId(3L);
        record.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        record.setSubmitTime(LocalDateTime.of(2026, 10, 7, 9, 0, 0));
        homework = new EduHomeworkDO();
        homework.setId(HOMEWORK_ID);
        homework.setTeacherId(TEACHER_ID);
        homework.setAutoGrade(1);
        homework.setAiReviewMode(1);
        homework.setSubmissionMode("text");
        when(recordMapper.selectById(RECORD_ID)).thenAnswer(invocation -> record);
        when(homeworkMapper.selectById(HOMEWORK_ID)).thenAnswer(invocation -> homework);
        when(evidenceService.build(any(), any())).thenReturn(new ReviewEvidence());
        when(calibrationService.calculate(any(), any())).thenReturn(new AiReviewCalibrationRespVO());
        AiEduReviewRespDTO response = new AiEduReviewRespDTO();
        response.setScore(82);
        response.setComment("结构清楚");
        response.setConfidence(0.9D);
        when(aiApi.correctHomework(any())).thenReturn(response);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ========== 事务提交后才派发 ==========

    @Test
    void enqueueInsideTransactionDispatchesOnlyAfterCommit() {
        beginTransaction();

        service.enqueuePreReview(RECORD_ID);

        EduHomeworkAiReviewDO queued = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, queued.getStatus());
        assertEquals(record.getSubmitTime(), queued.getSourceSubmitTime());
        assertEquals(0, executor.pending.size(), "事务提交前不能派发");

        commit();

        assertEquals(1, executor.pending.size());
        executor.runAll();
        assertEquals(HomeworkAiReviewStatusEnum.READY, table.only().getStatus());
        assertEquals(82, table.only().getAiScore());
        assertFalse(dispatcher.isInFlight(queued.getId()));
    }

    @Test
    void rolledBackTransactionNeverDispatches() {
        beginTransaction();

        service.enqueuePreReview(RECORD_ID);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertEquals(0, executor.pending.size());
        verifyNoInteractions(aiApi);
    }

    @Test
    void enqueueWithoutTransactionDispatchesImmediately() {
        service.enqueuePreReview(RECORD_ID);

        assertEquals(1, executor.pending.size());
        executor.runAll();
        assertEquals(HomeworkAiReviewStatusEnum.READY, table.only().getStatus());
    }

    // ========== 线程池拒绝：保持排队，由扫描补派 ==========

    @Test
    void rejectedDispatchStaysQueuedAndIsPickedUpByPoll() {
        executor.reject = true;

        service.enqueuePreReview(RECORD_ID);

        EduHomeworkAiReviewDO queued = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, queued.getStatus());
        assertNull(queued.getErrorMessage());
        assertFalse(dispatcher.isInFlight(queued.getId()), "被拒绝后要让出，等扫描再派");

        // 队列仍满：扫描也只是保持排队
        dispatcher.poll();
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, table.only().getStatus());

        executor.reject = false;
        dispatcher.poll();
        assertEquals(1, executor.pending.size());
        executor.runAll();
        assertEquals(HomeworkAiReviewStatusEnum.READY, table.only().getStatus());
        verify(aiApi, times(1)).correctHomework(any());
    }

    @Test
    void pollSkipsRowsAlreadyHandedToExecutorAndClaimRunsOnce() {
        service.enqueuePreReview(RECORD_ID);
        Long reviewId = table.only().getId();
        assertTrue(dispatcher.isInFlight(reviewId));

        dispatcher.poll();
        assertEquals(1, executor.pending.size(), "已在线程池里的不重复派发");

        // 另一实例或批量任务抢先认领：本实例的任务认领失败，不重复调用模型
        assertNotNull(service.processQueuedReview(reviewId));
        executor.runAll();
        assertNull(service.processQueuedReview(reviewId));
        verify(aiApi, times(1)).correctHomework(any());
        assertFalse(dispatcher.isInFlight(reviewId));
    }

    @Test
    void dispatchQueuedStopsAtFirstRejection() {
        executor.reject = true;
        service.enqueuePreReview(RECORD_ID);
        EduHomeworkRecordDO second = recordFor(2L);
        when(recordMapper.selectById(2L)).thenReturn(second);
        service.enqueuePreReview(2L);
        assertEquals(2, table.rows.size());
        executor.attempts = 0;

        assertEquals(0, dispatcher.dispatchQueued());
        assertEquals(1, executor.attempts, "第一条被拒绝后本轮不再尝试");
    }

    // ========== 入队规则 ==========

    @Test
    void enqueueIsIdempotentForTheSameSubmission() {
        executor.reject = true;
        service.enqueuePreReview(RECORD_ID);
        service.enqueuePreReview(RECORD_ID);

        assertEquals(1, table.rows.size());
    }

    @Test
    void resubmissionQueuesNewReviewAndSupersedesTheOldOne() {
        service.enqueuePreReview(RECORD_ID);
        record.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED);
        record.setSubmitTime(record.getSubmitTime().plusHours(1));
        service.enqueuePreReview(RECORD_ID);
        assertEquals(2, table.rows.size());

        executor.runAll();

        List<EduHomeworkAiReviewDO> rows = table.ordered();
        assertEquals(HomeworkAiReviewStatusEnum.SKIPPED, rows.get(0).getStatus());
        assertEquals(HomeworkAiReviewFailures.SUPERSEDED_SKIPPED, rows.get(0).getErrorMessage());
        assertEquals(HomeworkAiReviewStatusEnum.READY, rows.get(1).getStatus());
        verify(aiApi, times(1)).correctHomework(any());
    }

    @Test
    void homeworkWithoutAiReviewIsNotQueued() {
        homework.setAutoGrade(0);
        homework.setAiReviewMode(0);

        service.enqueuePreReview(RECORD_ID);

        assertTrue(table.rows.isEmpty());
        assertEquals(0, executor.pending.size());
    }

    @Test
    void legacyAutoGradeWithoutReviewModeIsTreatedAsAiPreReview() {
        homework.setAutoGrade(1);
        homework.setAiReviewMode(0);

        service.enqueuePreReview(RECORD_ID);
        executor.runAll();

        assertEquals(HomeworkAiReviewStatusEnum.READY, table.only().getStatus());
    }

    @Test
    void disabledSystemRecordsSkippedReviewWithReason() {
        properties.setEnabled(false);

        service.enqueuePreReview(RECORD_ID);

        assertEquals(HomeworkAiReviewStatusEnum.SKIPPED, table.only().getStatus());
        assertEquals(HomeworkAiReviewFailures.DISABLED_SKIPPED, table.only().getErrorMessage());
        assertEquals(0, executor.pending.size());
    }

    @Test
    void enqueueErrorDoesNotFailTheSubmission() {
        doThrow(new IllegalStateException("db")).when(table.mapper).insert(any(EduHomeworkAiReviewDO.class));

        assertDoesNotThrow(() -> service.enqueuePreReview(RECORD_ID));
    }

    // ========== 放弃执行的每条路径都写明原因 ==========

    @Test
    void missingRecordFailsWithReasonAndNoNotification() {
        service.enqueuePreReview(RECORD_ID);
        when(recordMapper.selectById(RECORD_ID)).thenReturn(null);

        executor.runAll();

        EduHomeworkAiReviewDO row = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.FAILED, row.getStatus());
        assertEquals(HomeworkAiReviewFailures.failedMessage(HomeworkAiReviewFailures.RECORD_MISSING_CAUSE, false),
                row.getErrorMessage());
        verifyNoInteractions(notifier, aiApi);
    }

    @Test
    void recordNoLongerPendingFailsWithReason() {
        service.enqueuePreReview(RECORD_ID);
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);

        executor.runAll();

        EduHomeworkAiReviewDO row = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.FAILED, row.getStatus());
        assertTrue(row.getErrorMessage().contains(HomeworkAiReviewFailures.WRONG_STATUS_CAUSE));
        verifyNoInteractions(notifier, aiApi);
    }

    @Test
    void modelErrorFailsWithReasonAndNotifiesTeacher() {
        when(aiApi.correctHomework(any())).thenThrow(new IllegalStateException("模型额度已用完"));
        service.enqueuePreReview(RECORD_ID);

        executor.runAll();

        EduHomeworkAiReviewDO row = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.FAILED, row.getStatus());
        assertEquals("AI 初评没有完成：AI 模型调用出错（模型额度已用完）。" + HomeworkAiReviewFailures.RETRY_HINT,
                row.getErrorMessage());
        verify(notifier).notifyFailure(HOMEWORK_ID, "AI 模型调用出错（模型额度已用完）");
    }

    @Test
    void modelTimeoutIsReportedAsTimeout() {
        when(aiApi.correctHomework(any())).thenThrow(new IllegalStateException("调用失败",
                new SocketTimeoutException("Read timed out")));
        service.enqueuePreReview(RECORD_ID);

        executor.runAll();

        assertEquals(HomeworkAiReviewFailures.failedMessage(HomeworkAiReviewFailures.MODEL_TIMEOUT_CAUSE, true),
                table.only().getErrorMessage());
        verify(notifier).notifyFailure(HOMEWORK_ID, HomeworkAiReviewFailures.MODEL_TIMEOUT_CAUSE);
    }

    @Test
    void attachmentErrorFailsWithReason() {
        when(evidenceService.build(any(), any())).thenThrow(new IllegalStateException("音频转写失败"));
        service.enqueuePreReview(RECORD_ID);

        executor.runAll();

        EduHomeworkAiReviewDO row = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.FAILED, row.getStatus());
        assertTrue(row.getErrorMessage().contains("提交的附件没能读取（音频转写失败）"));
        verify(notifier).notifyFailure(eq(HOMEWORK_ID), any());
        verifyNoInteractions(aiApi);
    }

    @Test
    void videoSubmissionIsSkippedWithReadableReason() {
        ReviewEvidence evidence = new ReviewEvidence();
        evidence.getVideoUrls().add("/file/video.mp4");
        when(evidenceService.build(any(), any())).thenReturn(evidence);
        service.enqueuePreReview(RECORD_ID);

        executor.runAll();

        assertEquals(HomeworkAiReviewStatusEnum.SKIPPED, table.only().getStatus());
        assertEquals(HomeworkAiReviewFailures.VIDEO_SKIPPED, table.only().getErrorMessage());
        verifyNoInteractions(aiApi, notifier);
    }

    @Test
    void staleQueuedReviewFailsAsQueueOverflowAndNotifies() {
        executor.reject = true;
        service.enqueuePreReview(RECORD_ID);
        Long reviewId = table.only().getId();
        table.age(reviewId, LocalDateTime.now().minusMinutes(121));

        dispatcher.poll();

        EduHomeworkAiReviewDO row = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.FAILED, row.getStatus());
        String cause = HomeworkAiReviewFailures.queueOverflowCause(120);
        assertEquals(HomeworkAiReviewFailures.failedMessage(cause, true), row.getErrorMessage());
        verify(notifier).notifyFailure(HOMEWORK_ID, cause);
    }

    @Test
    void staleProcessingReviewFailsAsTimeoutAndLateResultIsIgnored() {
        service.enqueuePreReview(RECORD_ID);
        Long reviewId = table.only().getId();
        // 模拟已认领但线程卡住
        assertEquals(1, table.mapper.claimQueued(reviewId, table.only().getGenerationToken()));
        table.age(reviewId, LocalDateTime.now().minusSeconds(2161));

        dispatcher.poll();

        assertEquals(HomeworkAiReviewStatusEnum.FAILED, table.only().getStatus());
        verify(notifier).notifyFailure(HOMEWORK_ID, HomeworkAiReviewFailures.PROCESSING_TIMEOUT_CAUSE);
        // 原线程迟到的执行认领不到，不会覆盖失败结果
        executor.runAll();
        assertEquals(HomeworkAiReviewStatusEnum.FAILED, table.only().getStatus());
        verifyNoInteractions(aiApi);
    }

    @Test
    void processingLeftOverByRestartResumesAfterStartupRequeue() {
        when(policy.requeueOnStartup()).thenReturn(true);
        service.enqueuePreReview(RECORD_ID);
        Long reviewId = table.only().getId();
        String oldToken = table.only().getGenerationToken();
        // 上一次运行已认领、还没写结果就重启了
        assertEquals(1, table.mapper.claimQueued(reviewId, oldToken));
        executor.pending.clear();
        table.age(reviewId, LocalDateTime.now().minusMinutes(2));
        HomeworkAiReviewDispatcher restarted = new HomeworkAiReviewDispatcher();
        ReflectionTestUtils.setField(restarted, "executor", executor);
        ReflectionTestUtils.setField(restarted, "reviewMapper", table.mapper);
        ReflectionTestUtils.setField(restarted, "reviewService", service);
        ReflectionTestUtils.setField(restarted, "policy", policy);
        ReflectionTestUtils.setField(restarted, "failureNotifier", notifier);

        assertEquals(1, restarted.requeueOnStartup());
        executor.runAll();

        EduHomeworkAiReviewDO row = table.only();
        assertEquals(HomeworkAiReviewStatusEnum.READY, row.getStatus());
        assertEquals(82, row.getAiScore());
        // 旧令牌的迟到结果不会覆盖
        EduHomeworkAiReviewDO late = HomeworkAiReviewFailures.failed(reviewId, "迟到");
        assertEquals(0, table.mapper.updateIfProcessing(late, reviewId, oldToken));
        verifyNoInteractions(notifier);
    }

    // ========== 教师手动触发 ==========

    @Test
    void manualTriggerRedispatchesQueuedReviewInsteadOfDuplicating() {
        EduAiBusinessAccessService access = mock(EduAiBusinessAccessService.class);
        when(access.requireRecordAccess(RECORD_ID)).thenReturn(record);
        ReflectionTestUtils.setField(service, "accessService", access);
        executor.reject = true;
        service.enqueuePreReview(RECORD_ID);
        executor.reject = false;

        service.triggerPreReviewAsync(RECORD_ID, true);

        assertEquals(1, table.rows.size());
        executor.runAll();
        assertEquals(HomeworkAiReviewStatusEnum.READY, table.only().getStatus());

        // 已生成后再次手动触发：重新生成一份
        service.triggerPreReviewAsync(RECORD_ID, true);
        executor.runAll();
        assertEquals(2, table.rows.size());
    }

    private EduHomeworkRecordDO recordFor(Long id) {
        EduHomeworkRecordDO other = new EduHomeworkRecordDO();
        other.setId(id);
        other.setHomeworkId(HOMEWORK_ID);
        other.setStudentId(4L);
        other.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        other.setSubmitTime(LocalDateTime.of(2026, 10, 7, 9, 5, 0));
        return other;
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private static void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    /** 可控线程池：任务先积压，由测试决定何时执行；reject=true 时模拟队列已满。 */
    static final class ControllableExecutor implements Executor {
        final List<Runnable> pending = new ArrayList<>();
        boolean reject;
        int attempts;

        @Override
        public void execute(Runnable command) {
            attempts++;
            if (reject) {
                throw new TaskRejectedException("队列已满");
            }
            pending.add(command);
        }

        void runAll() {
            while (!pending.isEmpty()) {
                pending.remove(0).run();
            }
        }
    }
}
