package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeworkAiReviewFailureNotifierTest extends BaseMockitoUnitTest {

    @InjectMocks
    private HomeworkAiReviewFailureNotifier notifier;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkAiReviewMapper reviewMapper;
    @Mock
    private EduBusinessNotifyService notifyService;
    @Mock
    private EduAiReviewPolicy policy;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 模拟 Redis 的 SET NX：键存在期间再次设置失败。 */
    private final Set<String> redisKeys = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(10L);
        homework.setTeacherId(7L);
        homework.setHomeworkName("教案设计");
        lenient().when(homeworkMapper.selectById(10L)).thenReturn(homework);
        lenient().when(policy.failNotifyHours()).thenReturn(6);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> redisKeys.add(invocation.getArgument(0)));
        lenient().when(notifyService.sendToUser(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void notifiesTeacherOncePerWindowWithFailCountAndRetryLink() {
        when(reviewMapper.countLatestFailedByHomeworkId(10L)).thenReturn(3L);

        assertTrue(notifier.notifyFailure(10L, "AI 模型响应超时"));
        assertFalse(notifier.notifyFailure(10L, "AI 模型响应超时"), "窗口期内不再重复通知");
        assertFalse(notifier.notifyFailure(10L, "另一原因"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(notifyService, times(1)).sendToUser(eq(7L),
                eq(EduBusinessNotifyService.TEACHER_HOMEWORK_AI_REVIEW_FAILED), params.capture(),
                eq("/capacity/homework_manager/homework/correct/10"));
        assertEquals("教案设计", params.getValue().get("homeworkName"));
        assertEquals(3L, params.getValue().get("failCount"));
        assertEquals("AI 模型响应超时", params.getValue().get("reason"));
        verify(valueOperations, times(3)).setIfAbsent(eq(HomeworkAiReviewFailureNotifier.DEDUP_KEY_PREFIX + "10"),
                anyString(), eq(Duration.ofHours(6)));
    }

    @Test
    void differentHomeworksAreNotifiedSeparately() {
        EduHomeworkDO other = new EduHomeworkDO();
        other.setId(11L);
        other.setTeacherId(8L);
        when(homeworkMapper.selectById(11L)).thenReturn(other);

        assertTrue(notifier.notifyFailure(10L, "原因"));
        assertTrue(notifier.notifyFailure(11L, "原因"));

        verify(notifyService).sendToUser(eq(7L), any(), any(), any());
        verify(notifyService).sendToUser(eq(8L), any(), any(), any());
    }

    @Test
    void windowLengthComesFromConfig() {
        when(policy.failNotifyHours()).thenReturn(24);

        notifier.notifyFailure(10L, "原因");

        verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void failCountIsAtLeastOneAndLongReasonIsTruncated() {
        when(reviewMapper.countLatestFailedByHomeworkId(10L)).thenReturn(0L);

        notifier.notifyFailure(10L, "很".repeat(200));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(notifyService).sendToUser(any(), any(), params.capture(), any());
        assertEquals(1L, params.getValue().get("failCount"));
        String reason = (String) params.getValue().get("reason");
        assertEquals(HomeworkAiReviewFailureNotifier.MAX_REASON_LENGTH + 1, reason.length());
        assertTrue(reason.endsWith("…"));
    }

    @Test
    void redisErrorSkipsNotificationWithoutThrowing() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertFalse(assertDoesNotThrow(() -> notifier.notifyFailure(10L, "原因")));
        verifyNoInteractions(notifyService);
    }

    @Test
    void missingHomeworkOrTeacherIsIgnored() {
        assertFalse(notifier.notifyFailure(99L, "原因"));
        assertFalse(notifier.notifyFailure(null, "原因"));
        verifyNoInteractions(notifyService, valueOperations);
    }
}
