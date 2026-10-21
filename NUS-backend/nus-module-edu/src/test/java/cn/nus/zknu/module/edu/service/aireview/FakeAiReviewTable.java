package cn.nus.zknu.module.edu.service.aireview;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 用内存表模拟 edu_homework_ai_review 的条件更新语义（状态 + 令牌），让测试覆盖真实的状态迁移。
 */
final class FakeAiReviewTable {

    final Map<Long, EduHomeworkAiReviewDO> rows = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(100);
    final EduHomeworkAiReviewMapper mapper = mock(EduHomeworkAiReviewMapper.class);

    FakeAiReviewTable() {
        lenient().when(mapper.insert(any(EduHomeworkAiReviewDO.class))).thenAnswer(invocation -> {
            EduHomeworkAiReviewDO row = invocation.getArgument(0);
            row.setId(ids.incrementAndGet());
            LocalDateTime now = LocalDateTime.now();
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.put(row.getId(), copy(row));
            return 1;
        });
        lenient().when(mapper.selectById(any())).thenAnswer(invocation -> {
            EduHomeworkAiReviewDO row = rows.get((Long) invocation.getArgument(0));
            return row == null ? null : copy(row);
        });
        lenient().when(mapper.selectLatestByRecordId(anyLong())).thenAnswer(invocation -> rows.values().stream()
                .filter(row -> Objects.equals(row.getHomeworkRecordId(), invocation.getArgument(0)))
                .max(Comparator.comparing(EduHomeworkAiReviewDO::getId))
                .map(FakeAiReviewTable::copy).orElse(null));
        lenient().when(mapper.claimQueued(anyLong(), anyString())).thenAnswer(invocation -> {
            EduHomeworkAiReviewDO claim = new EduHomeworkAiReviewDO();
            claim.setStatus(HomeworkAiReviewStatusEnum.PROCESSING);
            return conditionalUpdate(claim, invocation.getArgument(0), invocation.getArgument(1),
                    HomeworkAiReviewStatusEnum.QUEUED);
        });
        lenient().when(mapper.updateIfProcessing(any(), anyLong(), anyString())).thenAnswer(invocation ->
                conditionalUpdate(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        HomeworkAiReviewStatusEnum.PROCESSING));
        lenient().when(mapper.failIfQueued(any(), anyLong(), anyString())).thenAnswer(invocation ->
                conditionalUpdate(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        HomeworkAiReviewStatusEnum.QUEUED));
        lenient().when(mapper.requeueProcessing(anyLong(), anyString(), anyString())).thenAnswer(invocation -> {
            EduHomeworkAiReviewDO requeue = new EduHomeworkAiReviewDO();
            requeue.setStatus(HomeworkAiReviewStatusEnum.QUEUED);
            requeue.setGenerationToken(invocation.getArgument(2));
            return conditionalUpdate(requeue, invocation.getArgument(0), invocation.getArgument(1),
                    HomeworkAiReviewStatusEnum.PROCESSING);
        });
        lenient().when(mapper.selectQueued(anyInt())).thenAnswer(invocation -> rows.values().stream()
                .filter(row -> Objects.equals(row.getStatus(), HomeworkAiReviewStatusEnum.QUEUED))
                .sorted(Comparator.comparing(EduHomeworkAiReviewDO::getId))
                .limit(invocation.<Integer>getArgument(0))
                .map(FakeAiReviewTable::copy).toList());
        lenient().when(mapper.selectStale(anyInt(), any(), anyInt())).thenAnswer(invocation -> rows.values().stream()
                .filter(row -> Objects.equals(row.getStatus(), invocation.getArgument(0)))
                .filter(row -> row.getUpdateTime().isBefore(invocation.getArgument(1)))
                .limit(invocation.<Integer>getArgument(2))
                .map(FakeAiReviewTable::copy).toList());
    }

    private synchronized int conditionalUpdate(EduHomeworkAiReviewDO update, Long id, String token, int expectedStatus) {
        EduHomeworkAiReviewDO row = rows.get(id);
        if (row == null || !Objects.equals(row.getGenerationToken(), token)
                || !Objects.equals(row.getStatus(), expectedStatus)) {
            return 0;
        }
        BeanUtil.copyProperties(update, row, CopyOptions.create().ignoreNullValue().setIgnoreProperties("id"));
        row.setUpdateTime(LocalDateTime.now());
        return 1;
    }

    EduHomeworkAiReviewDO only() {
        if (rows.size() != 1) {
            throw new AssertionError("期望 1 条初评，实际 " + rows.size() + " 条");
        }
        return rows.values().iterator().next();
    }

    List<EduHomeworkAiReviewDO> ordered() {
        return rows.values().stream().sorted(Comparator.comparing(EduHomeworkAiReviewDO::getId)).toList();
    }

    void age(Long id, LocalDateTime updateTime) {
        rows.get(id).setUpdateTime(updateTime);
    }

    static EduHomeworkAiReviewDO copy(EduHomeworkAiReviewDO source) {
        EduHomeworkAiReviewDO target = new EduHomeworkAiReviewDO();
        BeanUtil.copyProperties(source, target);
        return target;
    }
}
