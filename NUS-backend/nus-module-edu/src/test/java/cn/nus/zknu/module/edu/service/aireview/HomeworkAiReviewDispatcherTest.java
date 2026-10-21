package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HomeworkAiReviewDispatcherTest {

    private final FakeAiReviewTable table = new FakeAiReviewTable();
    private final EduAiReviewPolicy policy = mock(EduAiReviewPolicy.class);
    private final HomeworkAiReviewFailureNotifier notifier = mock(HomeworkAiReviewFailureNotifier.class);
    private final EduHomeworkAiReviewService reviewService = mock(EduHomeworkAiReviewService.class);
    private final HomeworkAiReviewOutboxTest.ControllableExecutor executor = new HomeworkAiReviewOutboxTest.ControllableExecutor();
    private final HomeworkAiReviewDispatcher dispatcher = new HomeworkAiReviewDispatcher();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatcher, "executor", executor);
        ReflectionTestUtils.setField(dispatcher, "reviewMapper", table.mapper);
        ReflectionTestUtils.setField(dispatcher, "reviewService", reviewService);
        ReflectionTestUtils.setField(dispatcher, "policy", policy);
        ReflectionTestUtils.setField(dispatcher, "failureNotifier", notifier);
        when(policy.dispatchBatchSize()).thenReturn(2);
        when(policy.queueTimeoutMinutes()).thenReturn(30);
        when(policy.processingTimeoutSeconds()).thenReturn(600L);
        when(policy.dispatchIntervalSeconds()).thenReturn(15);
    }

    @Test
    void nextRunFollowsConfiguredIntervalAfterLastCompletion() {
        TriggerContext context = mock(TriggerContext.class);
        Instant last = Instant.parse("2026-10-07T01:00:00Z");
        when(context.lastCompletion()).thenReturn(last);

        assertEquals(last.plusSeconds(15), dispatcher.nextRunTrigger().nextExecution(context));

        when(policy.dispatchIntervalSeconds()).thenReturn(60);
        assertEquals(last.plusSeconds(60), dispatcher.nextRunTrigger().nextExecution(context),
                "改配置后下一轮即生效");
    }

    @Test
    void firstRunWaitsOneInterval() {
        TriggerContext context = mock(TriggerContext.class);
        Instant before = Instant.now();

        Instant next = dispatcher.nextRunTrigger().nextExecution(context);

        assertNotNull(next);
        assertFalse(next.isBefore(before.plusSeconds(15)));
    }

    @Test
    void pollDispatchesAtMostBatchSizeInQueueOrder() {
        Long first = queued(LocalDateTime.now());
        Long second = queued(LocalDateTime.now());
        queued(LocalDateTime.now());

        dispatcher.poll();

        assertEquals(2, executor.pending.size());
        executor.runAll();
        verify(reviewService).processQueuedReview(first);
        verify(reviewService).processQueuedReview(second);
        verifyNoMoreInteractions(reviewService);
    }

    @Test
    void staleQueuedFailureNotifiesOnlyWhenTheConditionalUpdateWins() {
        Long reviewId = queued(LocalDateTime.now().minusMinutes(31));
        // 另一实例刚好把它认领走：条件更新不命中，不写失败、不通知
        EduHomeworkAiReviewDO row = table.rows.get(reviewId);
        when(table.mapper.failIfQueued(any(), any(), any())).thenReturn(0);

        dispatcher.failStaleQueued();

        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, row.getStatus());
        verifyNoInteractions(notifier);
    }

    @Test
    void pollSurvivesErrors() {
        when(table.mapper.selectStale(anyInt(), any(), anyInt())).thenThrow(new IllegalStateException("db"));

        assertDoesNotThrow(dispatcher::pollSafely);
    }

    // ========== 启动续跑 ==========

    @Test
    void startupRequeuesLeftoverProcessingRowsWithNewTokenAndDispatchesThem() {
        when(policy.requeueOnStartup()).thenReturn(true);
        Long leftover = processing(LocalDateTime.now().minusMinutes(3));
        Long another = processing(LocalDateTime.now().minusSeconds(30));
        String oldToken = table.rows.get(leftover).getGenerationToken();

        assertEquals(2, dispatcher.requeueOnStartup());

        EduHomeworkAiReviewDO row = table.rows.get(leftover);
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, row.getStatus());
        assertNotEquals(oldToken, row.getGenerationToken(), "换新令牌，旧线程迟到的结果会被忽略");
        assertEquals(HomeworkAiReviewStatusEnum.QUEUED, table.rows.get(another).getStatus());
        assertEquals(2, executor.pending.size(), "放回排队后立即派发");
        executor.runAll();
        verify(reviewService).processQueuedReview(leftover);
        verify(reviewService).processQueuedReview(another);
        verify(table.mapper, never()).updateIfProcessing(any(), any(), any());
        verifyNoInteractions(notifier);
    }

    @Test
    void startupRequeueSkipsRowsClaimedByThisInstance() {
        when(policy.requeueOnStartup()).thenReturn(true);
        // 本实例启动后才认领的记录（update_time 晚于实例启动）不能被放回排队
        Long claimedNow = processing(LocalDateTime.now().plusSeconds(1));

        assertEquals(0, dispatcher.requeueOnStartup());

        assertEquals(HomeworkAiReviewStatusEnum.PROCESSING, table.rows.get(claimedNow).getStatus());
        assertEquals(0, executor.pending.size());
    }

    @Test
    void startupRequeueCanBeDisabled() {
        when(policy.requeueOnStartup()).thenReturn(false);
        Long leftover = processing(LocalDateTime.now().minusMinutes(3));

        assertEquals(0, dispatcher.requeueOnStartup());

        assertEquals(HomeworkAiReviewStatusEnum.PROCESSING, table.rows.get(leftover).getStatus());
        verify(table.mapper, never()).requeueProcessing(any(), any(), any());
        assertEquals(0, executor.pending.size());
    }

    @Test
    void startupRequeuePagesThroughManyRowsAndStopsWhenNothingChanges() {
        when(policy.requeueOnStartup()).thenReturn(true);
        when(policy.dispatchBatchSize()).thenReturn(200);
        int total = HomeworkAiReviewDispatcher.REQUEUE_PAGE_SIZE + 5;
        for (int i = 0; i < total; i++) {
            processing(LocalDateTime.now().minusMinutes(5));
        }

        assertEquals(total, dispatcher.requeueOnStartup());
        assertTrue(table.rows.values().stream()
                .allMatch(row -> row.getStatus() == HomeworkAiReviewStatusEnum.QUEUED));

        // 同一页全被别处改走（条件更新都不命中）时不会死循环
        for (int i = 0; i < HomeworkAiReviewDispatcher.REQUEUE_PAGE_SIZE; i++) {
            processing(LocalDateTime.now().minusMinutes(5));
        }
        doReturn(0).when(table.mapper).requeueProcessing(any(), any(), any());
        assertEquals(0, assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), dispatcher::requeueOnStartup));
    }

    @Test
    void startupRequeueErrorIsLoggedNotThrown() {
        when(policy.requeueOnStartup()).thenReturn(true);
        when(table.mapper.selectStale(anyInt(), any(), anyInt())).thenThrow(new IllegalStateException("db"));

        assertDoesNotThrow(dispatcher::onApplicationReady);
    }

    private Long processing(LocalDateTime updateTime) {
        Long id = queued(updateTime);
        EduHomeworkAiReviewDO row = table.rows.get(id);
        row.setStatus(HomeworkAiReviewStatusEnum.PROCESSING);
        return id;
    }

    private Long queued(LocalDateTime updateTime) {
        EduHomeworkAiReviewDO row = new EduHomeworkAiReviewDO();
        row.setHomeworkRecordId(1L);
        row.setHomeworkId(10L);
        row.setStatus(HomeworkAiReviewStatusEnum.QUEUED);
        row.setGenerationToken(UUID.randomUUID().toString());
        table.mapper.insert(row);
        table.age(row.getId(), updateTime);
        return row.getId();
    }
}
