package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduPublicCourseSyncSchedulerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduPublicCourseSyncScheduler scheduler;
    @Mock
    private EduPublicCourseSyncService syncService;
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "executor", (Executor) tasks::add);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void transactionCommitOnlyEnqueuesAndWorkerDoesTheSync() {
        beginTransaction();

        scheduler.requestAll();
        scheduler.requestClass(3L);

        assertTrue(tasks.isEmpty());
        verifyNoInteractions(syncService);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertEquals(2, tasks.size());
        verifyNoInteractions(syncService);
        tasks.remove().run();
        tasks.remove().run();
        verify(syncService).syncAll();
        verify(syncService).syncClass(3L);
    }

    @Test
    void rollbackNeverEnqueuesWork() {
        beginTransaction();
        scheduler.requestAll();
        scheduler.requestClass(3L);

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertTrue(tasks.isEmpty());
        verifyNoInteractions(syncService);
    }

    @Test
    void queuedDuplicatesAreCoalescedPerScopeAndCanBeScheduledAgainAfterCompletion() {
        scheduler.submitCourse(2L);
        scheduler.submitCourse(2L);
        scheduler.submitCourse(4L);
        scheduler.requestAll();
        scheduler.requestAll();
        scheduler.requestClass(3L);
        scheduler.requestClass(3L);
        assertEquals(4, tasks.size());

        while (!tasks.isEmpty()) {
            tasks.remove().run();
        }
        verify(syncService).syncCourse(2L);
        verify(syncService).syncCourse(4L);
        verify(syncService).syncAll();
        verify(syncService).syncClass(3L);

        scheduler.submitCourse(2L);
        assertEquals(1, tasks.size());
        tasks.remove().run();
        verify(syncService, times(2)).syncCourse(2L);
    }

    @Test
    void changesCommittedDuringSyncAreMergedIntoOneMorePassWithoutAnotherQueuedTask() {
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (first.getAndSet(false)) {
                scheduler.submitCourse(2L);
                scheduler.submitCourse(2L);
                scheduler.submitCourse(2L);
                assertTrue(tasks.isEmpty());
            }
            return null;
        }).when(syncService).syncCourse(2L);
        scheduler.submitCourse(2L);

        tasks.remove().run();

        assertTrue(tasks.isEmpty());
        verify(syncService, times(2)).syncCourse(2L);
    }

    @Test
    void rejectionDoesNotRunOnCallerAndReleasesScopeForDailyRecovery() {
        ReflectionTestUtils.setField(scheduler, "executor", (Executor) task -> {
            throw new RejectedExecutionException("队列已满");
        });

        assertDoesNotThrow(() -> scheduler.submitCourse(2L));
        assertDoesNotThrow(scheduler::requestAll);
        verifyNoInteractions(syncService);

        ReflectionTestUtils.setField(scheduler, "executor", (Executor) tasks::add);
        scheduler.submitCourse(2L);
        scheduler.requestAll();
        assertEquals(2, tasks.size());
        tasks.remove().run();
        tasks.remove().run();
        verify(syncService).syncCourse(2L);
        verify(syncService).syncAll();
    }

    @Test
    void failedWorkerReleasesScopeForRetry() {
        doThrow(new IllegalStateException("数据库暂不可用")).doNothing().when(syncService).syncCourse(2L);
        scheduler.submitCourse(2L);
        assertDoesNotThrow(() -> tasks.remove().run());

        scheduler.submitCourse(2L);
        assertEquals(1, tasks.size());
        tasks.remove().run();
        verify(syncService, times(2)).syncCourse(2L);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
}
