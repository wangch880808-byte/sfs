package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduReviewTaskDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduReviewTaskMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 批量初评任务的恢复；单份初评的排队与超时见 HomeworkAiReviewDispatcherTest。 */
class AiReviewTaskRecovererTest extends BaseMockitoUnitTest {

    /** 默认模型硬超时 180 秒、5 个附件时的单份上限。 */
    private static final long DEADLINE_SECONDS = 2160L;

    @InjectMocks private AiReviewTaskRecoverer recoverer;
    @Mock private AiEduReviewTaskMapper taskMapper;
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private AiEduReviewServiceImpl reviewService;
    @Mock private EduAiReviewPolicy reviewPolicy;
    @Mock private Executor executor;

    @Test
    void requeuesUnclaimedBatchOnStartup() {
        when(reviewPolicy.processingTimeoutSeconds()).thenReturn(DEADLINE_SECONDS);
        AiEduReviewTaskDO task = task(1L, null, LocalDateTime.now());
        task.setTeacherId(7L);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(homeworkMapper.selectById(10L)).thenReturn(new EduHomeworkDO());

        recoverer.recoverOnStartup();

        verify(executor, times(1)).execute(any());
    }

    @Test
    void periodicScanLeavesFreshQueuedBatchUntouched() {
        when(reviewPolicy.processingTimeoutSeconds()).thenReturn(DEADLINE_SECONDS);
        AiEduReviewTaskDO task = task(1L, null, LocalDateTime.now());
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        recoverer.recoverPeriodically();

        verify(taskMapper, never()).failIfUnclaimed(any(), any(), any());
        verifyNoInteractions(executor, homeworkMapper);
    }

    @Test
    void periodicScanFailsStaleQueuedBatchWithoutRequeue() {
        when(reviewPolicy.processingTimeoutSeconds()).thenReturn(DEADLINE_SECONDS);
        LocalDateTime old = LocalDateTime.now().minusHours(2);
        AiEduReviewTaskDO task = task(1L, null, old);
        task.setTeacherId(7L);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        recoverer.recoverPeriodically();

        verify(taskMapper).failIfUnclaimed(eq(1L), eq(1), eq("AI 批量初评排队超时，请重新发起"));
        verifyNoInteractions(executor, homeworkMapper);
    }

    @Test
    void failsStaleClaimedBatchConditionally() {
        when(reviewPolicy.processingTimeoutSeconds()).thenReturn(DEADLINE_SECONDS);
        LocalDateTime old = LocalDateTime.now().minusHours(2);
        AiEduReviewTaskDO task = task(1L, "owner", old);
        task.setTotalCount(2);
        task.setSuccessCount(1);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        recoverer.recoverPeriodically();

        ArgumentCaptor<AiEduReviewTaskDO> batchFailure = ArgumentCaptor.forClass(AiEduReviewTaskDO.class);
        verify(taskMapper).failIfClaimedWithCounts(batchFailure.capture(), eq(task));
        assertEquals(2, batchFailure.getValue().getStatus());
        assertEquals(1, batchFailure.getValue().getFailCount());
        verifyNoInteractions(executor);
    }

    @Test
    void periodicScanUsesRecentProgressOfClaimedBatch() {
        when(reviewPolicy.processingTimeoutSeconds()).thenReturn(DEADLINE_SECONDS);
        LocalDateTime old = LocalDateTime.now().minusHours(2);
        AiEduReviewTaskDO task = task(1L, "owner", old);
        task.setUpdateTime(LocalDateTime.now());
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        recoverer.recoverPeriodically();

        verify(taskMapper, never()).failIfClaimedWithCounts(any(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void losingClaimDoesNotStartDuplicateBatch() {
        AiEduReviewServiceImpl service = new AiEduReviewServiceImpl();
        AiEduReviewTaskMapper mapper = mock(AiEduReviewTaskMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reviewTaskMapper", mapper);
        AiEduReviewTaskDO task = task(1L, null, LocalDateTime.now());
        when(mapper.selectById(1L)).thenReturn(task);
        when(mapper.claim(eq(1L), any())).thenReturn(0);

        service.generateDrafts(1L);

        verify(mapper).claim(eq(1L), any());
        verify(mapper, never()).updateIfClaimed(any(), any(), any());
        assertEquals(0, task.getStatus());
    }

    private static AiEduReviewTaskDO task(Long id, String token, LocalDateTime created) {
        AiEduReviewTaskDO task = new AiEduReviewTaskDO();
        task.setId(id);
        task.setHomeworkId(10L);
        task.setStatus(0);
        task.setTotalCount(1);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setGenerationToken(token);
        task.setCreateTime(created);
        return task;
    }
}
