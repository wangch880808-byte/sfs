package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchMapper;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchItemMapper;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduImportBatchAsyncTest extends BaseMockitoUnitTest {

    @Mock private EduImportHandler handler;
    @Mock private EduImportBatchMapper batchMapper;
    @Mock private EduImportBatchItemMapper itemMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TaskExecutor executor;
    @Mock private ConfigApi configApi;
    private EduImportBatchServiceImpl service;

    @BeforeEach
    void setUp() {
        when(handler.getBizType()).thenReturn("TEST");
        when(handler.getTemplateVersion()).thenReturn("1.0");
        service = new EduImportBatchServiceImpl(List.of(handler));
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(service, "importExecutor", executor);
        ReflectionTestUtils.setField(service, "configApi", configApi);
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void confirm_returnsConfirmingAfterClaimCommitAndSchedulesOnce() {
        readyBatch();
        try (var security = login()) {
            var response = service.confirm("IMP-1", "  newPass1  ");
            assertEquals("CONFIRMING", response.getStatus());
            assertNotNull(response.getConfirmTime());
            var order = inOrder(handler, batchMapper, transactionManager, executor);
            order.verify(handler).validatePermission();
            order.verify(transactionManager).getTransaction(argThat(def ->
                    def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
            order.verify(batchMapper).claimForConfirmation(eq(1L), eq("PREVIEW_READY"), eq("CONFIRMING"), any());
            order.verify(transactionManager).commit(any());
            order.verify(executor).execute(any());
            verify(handler, never()).commit(any(), any(), any());
        }
    }

    @Test
    void async_successCommitsValidRowsCountsAndTrimmedPasswordTogether() {
        var batch = readyBatch();
        when(batchMapper.selectById(1L)).thenReturn(batch);
        var valid = item("VALID", "INSERT", "{}");
        var invalid = item("INVALID", "INSERT", "{}");
        when(itemMapper.selectListByBatchId(1L)).thenReturn(List.of(valid, invalid));
        when(handler.commit(batch, List.of(valid), "newPass1")).thenReturn(new EduImportCommitResult(3, 2, 1));
        when(batchMapper.completeConfirmation(batch)).thenReturn(1);
        try (var security = login()) {
            var response = service.confirm("IMP-1", "  newPass1  ");
            submittedTask().run();
            assertEquals("CONFIRMING", response.getStatus());
            assertEquals("SUCCESS", batch.getStatus());
            assertEquals(3, batch.getInsertedCount());
            assertEquals(2, batch.getUpdatedCount());
            assertEquals(1, batch.getSkippedCount());
            assertNotNull(batch.getConfirmTime());
        }
        verify(batchMapper).completeConfirmation(batch);
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager, never()).rollback(any());
        verify(batchMapper, never()).failConfirmation(any(), any());
    }

    @Test
    void async_serviceExceptionRollsBackThenPersistsItsMessageInNewTransaction() {
        assertAsyncFailure(new ServiceException(123, "本学院无权导入该学生"), "本学院无权导入该学生");
    }

    @Test
    void async_unexpectedExceptionRollsBackThenPersistsGenericMessage() {
        assertAsyncFailure(new IllegalStateException("internal database detail"), EduImportBatchServiceImpl.FAILURE_REASON);
    }

    @Test
    void async_missingSuccessUpdateRollsBackBeforeFailureTransaction() {
        var batch = readyBatch();
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(handler.commit(eq(batch), eq(List.of()), isNull())).thenReturn(new EduImportCommitResult(3, 2, 1));
        try (var security = login()) {
            service.confirm("IMP-1", null);
            submittedTask().run();
        }
        var order = inOrder(batchMapper, transactionManager);
        order.verify(batchMapper).completeConfirmation(batch);
        order.verify(transactionManager).rollback(any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(batchMapper).failConfirmation(1L, EduImportBatchServiceImpl.FAILURE_REASON);
        order.verify(transactionManager).commit(any());
    }

    @Test
    void async_timeoutDuringCommitPreventsBusinessTransactionFromCommitting() {
        var persisted = readyBatch();
        var workerBatch = new EduImportBatchDO();
        org.springframework.beans.BeanUtils.copyProperties(persisted, workerBatch);
        workerBatch.setStatus("CONFIRMING");
        when(batchMapper.selectById(1L)).thenReturn(workerBatch);
        when(handler.commit(eq(workerBatch), eq(List.of()), isNull())).thenAnswer(invocation -> {
            persisted.setStatus("FAILED");
            persisted.setFailureReason(EduImportBatchServiceImpl.TIMEOUT_REASON);
            return new EduImportCommitResult(3, 2, 1);
        });
        // 超时回收已提交 FAILED，完成状态的条件更新和失败回写均不再命中 CONFIRMING。
        try (var security = login()) {
            service.confirm("IMP-1", null);
            submittedTask().run();
            var response = service.get("IMP-1");
            assertEquals("FAILED", response.getStatus());
            assertEquals(EduImportBatchServiceImpl.TIMEOUT_REASON, response.getFailureReason());
        }
        verify(transactionManager).rollback(any());
        verify(batchMapper).completeConfirmation(workerBatch);
    }

    @Test
    void cancel_losingConfirmationRaceCannotOverwriteConfirming() {
        ownedBatch("PREVIEW_READY");
        try (var security = login()) {
            assertServiceException(() -> service.cancel("IMP-1"), IMPORT_BATCH_STATUS_INVALID);
        }
        verify(batchMapper).cancelPreview(1L);
        verify(batchMapper, never()).completeConfirmation(any(EduImportBatchDO.class));
    }

    private void assertAsyncFailure(RuntimeException failure, String expectedReason) {
        var batch = readyBatch();
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(handler.commit(eq(batch), eq(List.of()), isNull())).thenThrow(failure);
        doAnswer(invocation -> {
            batch.setStatus("FAILED");
            batch.setFailureReason(invocation.getArgument(1));
            return 1;
        }).when(batchMapper).failConfirmation(1L, expectedReason);
        try (var security = login()) {
            service.confirm("IMP-1", null);
            submittedTask().run();
            assertEquals("FAILED", batch.getStatus());
            assertEquals(expectedReason, service.get("IMP-1").getFailureReason());
        }
        var order = inOrder(transactionManager, batchMapper);
        order.verify(transactionManager).getTransaction(any());
        order.verify(batchMapper).claimForConfirmation(eq(1L), any(), any(), any());
        order.verify(transactionManager).commit(any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(batchMapper).selectById(1L);
        order.verify(transactionManager).rollback(any());
        order.verify(transactionManager).getTransaction(argThat(def ->
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(batchMapper).failConfirmation(1L, expectedReason);
        order.verify(transactionManager).commit(any());
        verify(batchMapper, never()).completeConfirmation(any(EduImportBatchDO.class));
    }

    @Test
    void confirm_repeatedConfirmingReturnsStatusWithoutResubmissionEvenAfterPreviewExpiry() {
        var batch = ownedBatch("CONFIRMING");
        batch.setExpireTime(LocalDateTime.now().minusHours(1));
        try (var security = login()) {
            assertEquals("CONFIRMING", service.confirm("IMP-1", null).getStatus());
            assertEquals("CONFIRMING", service.confirm("IMP-1", null).getStatus());
        }
        verifyNoInteractions(executor, transactionManager, itemMapper);
        verify(handler, never()).validatePermission();
    }

    @Test
    void confirm_successRemainsIdempotent() {
        ownedBatch("SUCCESS");
        try (var security = login()) {
            assertEquals("SUCCESS", service.confirm("IMP-1", null).getStatus());
        }
        verifyNoInteractions(executor, transactionManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "CANCELLED", "PREVIEW_INVALID"})
    void confirm_otherStatusesAreRejected(String status) {
        ownedBatch(status);
        try (var security = login()) {
            assertServiceException(() -> service.confirm("IMP-1", null), IMPORT_BATCH_STATUS_INVALID);
        }
        verifyNoInteractions(executor, transactionManager);
    }

    @Test
    void confirm_permissionDeniedBeforeClaimOrSubmission() {
        ownedBatch("PREVIEW_READY");
        var denied = new ServiceException(403, "无导入权限");
        doThrow(denied).when(handler).validatePermission();
        try (var security = login()) {
            assertSame(denied, assertThrows(ServiceException.class, () -> service.confirm("IMP-1", null)));
        }
        verifyNoInteractions(executor, transactionManager);
    }

    @Test
    void confirm_invalidRowsAndExpiryStillRejected() {
        var batch = ownedBatch("PREVIEW_READY");
        batch.setInvalidCount(1);
        try (var security = login()) {
            assertServiceException(() -> service.confirm("IMP-1", null), IMPORT_BATCH_HAS_ERRORS);
            batch.setInvalidCount(0);
            batch.setExpireTime(LocalDateTime.now().minusMinutes(1));
            assertServiceException(() -> service.confirm("IMP-1", null), IMPORT_BATCH_EXPIRED);
        }
        verifyNoInteractions(executor, transactionManager);
    }

    @Test
    void confirm_losingConcurrentClaimReturnsCurrentStatusWithoutSubmission() {
        var preview = ownedBatch("PREVIEW_READY");
        var confirming = new EduImportBatchDO();
        org.springframework.beans.BeanUtils.copyProperties(preview, confirming);
        confirming.setStatus("CONFIRMING");
        when(batchMapper.selectByBatchNo("IMP-1")).thenReturn(preview, confirming);
        try (var security = login()) {
            assertEquals("CONFIRMING", service.confirm("IMP-1", null).getStatus());
        }
        verifyNoInteractions(executor);
        verify(transactionManager).commit(any());
    }

    @Test
    void confirm_queueFullResetsPreviewAndClearsStartTimeBeforeReportingBusy() {
        var batch = readyBatch();
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        doAnswer(invocation -> {
            batch.setStatus("PREVIEW_READY");
            batch.setConfirmTime(null);
            return 1;
        }).when(batchMapper).resetConfirmation(1L);
        try (var security = login()) {
            assertServiceException(() -> service.confirm("IMP-1", "newPass1"), IMPORT_EXECUTOR_BUSY);
        }
        assertEquals("PREVIEW_READY", batch.getStatus());
        assertNull(batch.getConfirmTime());
        var order = inOrder(transactionManager, executor, batchMapper);
        order.verify(transactionManager).getTransaction(any());
        order.verify(batchMapper).claimForConfirmation(eq(1L), any(), any(), any());
        order.verify(transactionManager).commit(any());
        order.verify(executor).execute(any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(batchMapper).resetConfirmation(1L);
        order.verify(transactionManager).commit(any());
        verify(handler, never()).commit(any(), any(), any());
    }

    @Test
    void get_timedOutConfirmingBecomesFailedWithReason() {
        var batch = ownedBatch("CONFIRMING");
        batch.setConfirmTime(LocalDateTime.now().minusMinutes(31));
        doAnswer(invocation -> {
            batch.setStatus("FAILED");
            batch.setFailureReason(invocation.getArgument(2));
            return 1;
        }).when(batchMapper).failTimedOutConfirmations(eq(1L), any(), eq(EduImportBatchServiceImpl.TIMEOUT_REASON));
        try (var security = login()) {
            var response = service.get("IMP-1");
            assertEquals("FAILED", response.getStatus());
            assertEquals(EduImportBatchServiceImpl.TIMEOUT_REASON, response.getFailureReason());
        }
        verify(transactionManager).commit(any());
    }

    @Test
    void get_freshConfirmingIsNotFailed() {
        var batch = ownedBatch("CONFIRMING");
        batch.setConfirmTime(LocalDateTime.now().minusMinutes(29));
        try (var security = login()) {
            assertEquals("CONFIRMING", service.get("IMP-1").getStatus());
        }
        verifyNoInteractions(transactionManager);
    }

    @Test
    void get_timeoutUsesConfiguredMinutes() {
        var batch = ownedBatch("CONFIRMING");
        batch.setConfirmTime(LocalDateTime.now().minusMinutes(40));
        when(configApi.getConfigValueByKey(EduImportBatchServiceImpl.CONFIRM_TIMEOUT_KEY)).thenReturn(" 60 ");
        try (var security = login()) {
            assertEquals("CONFIRMING", service.get("IMP-1").getStatus());
        }
        verifyNoInteractions(transactionManager);
    }

    @Test
    void get_successWonTimeoutRaceIsReturnedWithoutFalseFailure() {
        var batch = ownedBatch("CONFIRMING");
        batch.setConfirmTime(LocalDateTime.now().minusMinutes(31));
        doAnswer(invocation -> {
            batch.setStatus("SUCCESS");
            return 0;
        }).when(batchMapper).failTimedOutConfirmations(eq(1L), any(), any());
        try (var security = login()) {
            assertEquals("SUCCESS", service.get("IMP-1").getStatus());
        }
        verify(batchMapper, times(2)).selectByBatchNo("IMP-1");
    }

    @Test
    void get_successIncludesSkipReasonCountsOnlyForValidSkippedRows() {
        ownedBatch("SUCCESS");
        when(itemMapper.selectSkipReasonRows(1L)).thenReturn(List.of(
                item("VALID", "SKIP", "{\"skipReason\":\"UNCHANGED\"}"),
                item("VALID", "SKIP", "{\"skipReason\":\"UNCHANGED\"}"),
                item("VALID", "SKIP", "{\"skipReason\":\"EMPTY\"}"),
                item("valid", "SKIP", "{\"skipReason\":\"EMPTY\"}"),
                item("VALID", "SKIP ", "{\"skipReason\":\"EMPTY\"}"),
                item("INVALID", "SKIP", "{\"skipReason\":\"EMPTY\"}"),
                item("VALID", "UPDATE", "{\"skipReason\":\"EMPTY\"}"),
                item("VALID", "SKIP", "broken json"),
                item("VALID", "SKIP", "{\"skipReason\":\"  \"}")));
        try (var security = login()) {
            assertEquals(Map.of("UNCHANGED", 2, "EMPTY", 1), service.get("IMP-1").getSkipReasonCounts());
        }
        verify(itemMapper).selectSkipReasonRows(1L);
        verify(itemMapper, never()).selectListByBatchId(any());
    }

    @Test
    void async_doesNotWriteAfterQueuedBatchWasRecovered() {
        var batch = readyBatch();
        when(batchMapper.selectById(1L)).thenReturn(batch);
        try (var security = login()) {
            service.confirm("IMP-1", null);
            batch.setStatus("FAILED");
            submittedTask().run();
        }
        verify(handler, never()).commit(any(), any(), any());
        verifyNoInteractions(itemMapper);
        verify(batchMapper, never()).completeConfirmation(any(EduImportBatchDO.class));
    }

    @Test
    void startup_failsAllConfirmingStartedBeforeBootInIndependentTransaction() {
        when(batchMapper.failTimedOutConfirmations(isNull(), any(), eq(EduImportBatchServiceImpl.TIMEOUT_REASON)))
                .thenReturn(2);
        LocalDateTime before = LocalDateTime.now();
        service.run(null);
        verify(batchMapper).failTimedOutConfirmations(isNull(), argThat(cutoff ->
                !cutoff.isBefore(before) && !cutoff.isAfter(LocalDateTime.now())),
                eq(EduImportBatchServiceImpl.TIMEOUT_REASON));
        verify(transactionManager).getTransaction(argThat(def ->
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactionManager).commit(any());
        verifyNoInteractions(configApi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad", "1.5", ""})
    void get_invalidConfigFallsBackToThirtyMinutes(String value) {
        when(configApi.getConfigValueByKey(EduImportBatchServiceImpl.CONFIRM_TIMEOUT_KEY)).thenReturn(value);
        var batch = ownedBatch("CONFIRMING");
        batch.setConfirmTime(LocalDateTime.now().minusMinutes(31));
        try (var security = login()) {
            service.get("IMP-1");
        }
        verify(batchMapper).failTimedOutConfirmations(eq(1L), argThat(cutoff ->
                cutoff.isBefore(LocalDateTime.now().minusMinutes(29))
                        && cutoff.isAfter(LocalDateTime.now().minusMinutes(31))), eq(EduImportBatchServiceImpl.TIMEOUT_REASON));
    }

    private EduImportBatchDO readyBatch() {
        var batch = ownedBatch("PREVIEW_READY");
        when(batchMapper.claimForConfirmation(eq(1L), eq("PREVIEW_READY"), eq("CONFIRMING"), any())).thenReturn(1);
        return batch;
    }

    private EduImportBatchDO ownedBatch(String status) {
        var batch = new EduImportBatchDO();
        batch.setId(1L);
        batch.setBatchNo("IMP-1");
        batch.setOperatorId(7L);
        batch.setBizType("TEST");
        batch.setTemplateVersion("1.0");
        batch.setStatus(status);
        batch.setInvalidCount(0);
        batch.setExpireTime(LocalDateTime.now().plusMinutes(5));
        when(batchMapper.selectByBatchNo("IMP-1")).thenReturn(batch);
        return batch;
    }

    private EduImportBatchItemDO item(String validation, String action, String data) {
        var item = new EduImportBatchItemDO();
        item.setValidationStatus(validation);
        item.setAction(action);
        item.setNormalizedData(data);
        return item;
    }

    private Runnable submittedTask() {
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        return task.getValue();
    }

    private MockedStatic<SecurityFrameworkUtils> login() {
        var security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        return security;
    }
}
