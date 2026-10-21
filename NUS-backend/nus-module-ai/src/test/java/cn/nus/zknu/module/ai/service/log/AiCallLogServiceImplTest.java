package cn.nus.zknu.module.ai.service.log;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogDailySummaryRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogSummaryReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.log.AiCallLogDO;
import cn.nus.zknu.module.ai.dal.mysql.log.AiCallLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiCallLogServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiCallLogServiceImpl service;
    @Mock
    private AiCallLogMapper mapper;

    @BeforeEach
    void setUp() {
        service.setWriter(Runnable::run);
    }

    @Test
    void insertFailureIsSwallowed() {
        when(mapper.insert(any(AiCallLogDO.class))).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> service.record(new AiCallLogDO()));
    }

    @Test
    void fullQueueDropsTheLogWithoutFailingTheCall() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        service.setWriter(rejecting);
        assertDoesNotThrow(() -> service.record(new AiCallLogDO()));
        verifyNoInteractions(mapper);
    }

    @Test
    void cleanExpiredDeletesInBatchesUntilShortBatch() {
        when(mapper.deleteByCreateTimeLt(any(), eq(1000))).thenReturn(1000, 1000, 12);

        assertEquals(2012, service.cleanExpired(180));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(3)).deleteByCreateTimeLt(captor.capture(), eq(1000));
        LocalDateTime expected = LocalDateTime.now().minusDays(180);
        assertTrue(Math.abs(java.time.Duration.between(expected, captor.getValue()).toSeconds()) < 5);
    }

    @Test
    void dailySummaryConvertsDriverTypesAndDefaultsToLastWeek() {
        when(mapper.selectDailySummary(any(), any(), eq("HOMEWORK_REVIEW"))).thenReturn(List.of(Map.of(
                "statDate", java.sql.Date.valueOf(LocalDate.of(2026, 10, 6)),
                "BUSINESSCODE", "HOMEWORK_REVIEW",
                "callCount", 5L,
                "failedCount", new BigDecimal("1"),
                "promptTokens", new BigDecimal("300"),
                "completionTokens", new BigDecimal("200"),
                "totalTokens", new BigDecimal("500"))));
        AiCallLogSummaryReqVO req = new AiCallLogSummaryReqVO();
        req.setBusinessCode("HOMEWORK_REVIEW");

        List<AiCallLogDailySummaryRespVO> rows = service.getDailySummary(req);

        assertEquals(1, rows.size());
        AiCallLogDailySummaryRespVO row = rows.get(0);
        assertEquals(LocalDate.of(2026, 10, 6), row.getDate());
        assertEquals("任务 AI 初评", row.getBusinessName());
        assertEquals(5L, row.getCallCount());
        assertEquals(1L, row.getFailedCount());
        assertEquals(500L, row.getTotalTokens());
        ArgumentCaptor<LocalDateTime> begin = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectDailySummary(begin.capture(), end.capture(), eq("HOMEWORK_REVIEW"));
        assertEquals(LocalDate.now().plusDays(1).atStartOfDay(), end.getValue());
        assertEquals(end.getValue().minusDays(7), begin.getValue()); // 含今天共 7 天
    }

    @Test
    void dailySummaryRangeIsCapped() {
        when(mapper.selectDailySummary(any(), any(), any())).thenReturn(List.of());
        AiCallLogSummaryReqVO req = new AiCallLogSummaryReqVO();
        LocalDateTime end = LocalDateTime.of(2026, 10, 7, 0, 0);
        req.setCreateTime(new LocalDateTime[]{end.minusYears(2), end});

        service.getDailySummary(req);

        verify(mapper).selectDailySummary(eq(end.minusDays(93)), eq(end), eq(null));
    }
}
