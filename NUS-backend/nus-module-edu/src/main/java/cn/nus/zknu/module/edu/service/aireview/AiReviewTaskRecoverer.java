package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduReviewTaskDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduReviewTaskMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 批量初评任务的恢复：重启补投未认领的批量任务，周期性关闭超时的批量任务；所有状态变化均带原状态条件。
 * 单份初评的排队与超时由 {@link HomeworkAiReviewDispatcher} 负责。
 */
@Component
@Slf4j
public class AiReviewTaskRecoverer {

    @Resource private AiEduReviewTaskMapper taskMapper;
    @Resource private EduHomeworkMapper homeworkMapper;
    @Resource private AiEduReviewServiceImpl reviewService;
    @Resource private EduAiReviewPolicy reviewPolicy;
    @Resource(name = "eduAiReviewTaskExecutor") private Executor executor;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover(true);
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void recoverPeriodically() {
        recover(false);
    }

    private void recover(boolean startup) {
        // 单份评阅还可能串行抽取最多 5 个附件，每个音频转写最多 5 分钟；超时时长与单份初评一致。
        long perRecordDeadlineSeconds = reviewPolicy.processingTimeoutSeconds();
        LocalDateTime now = LocalDateTime.now();
        List<AiEduReviewTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiEduReviewTaskDO>()
                .eq(AiEduReviewTaskDO::getStatus, 0));
        for (AiEduReviewTaskDO task : tasks) {
            if (task.getGenerationToken() == null) {
                if (startup) {
                    if (task.getTeacherId() == null || homeworkMapper.selectById(task.getHomeworkId()) == null) {
                        taskMapper.failIfUnclaimed(task.getId(), value(task.getFailCount()) + remaining(task),
                                "历史批量任务缺少发起教师或任务已删除，无法安全重跑");
                    } else {
                        try {
                            executor.execute(() -> reviewService.generateDrafts(task.getId()));
                        } catch (RuntimeException ex) {
                            log.warn("[recoverAiReview] batch queue busy, taskId={}", task.getId(), ex);
                        }
                    }
                } else if (isStale(task.getCreateTime(), now, perRecordDeadlineSeconds)) {
                    taskMapper.failIfUnclaimed(task.getId(), value(task.getFailCount()) + remaining(task),
                            "AI 批量初评排队超时，请重新发起");
                }
            } else if (isStale(lastActivity(task.getUpdateTime(), task.getCreateTime()),
                    now, perRecordDeadlineSeconds)) {
                AiEduReviewTaskDO failed = new AiEduReviewTaskDO();
                failed.setStatus(2);
                failed.setFailCount(value(task.getFailCount()) + remaining(task));
                failed.setErrorMessage("AI 批量初评超时或服务重启，未完成的记录已标记失败");
                taskMapper.failIfClaimedWithCounts(failed, task);
            }
        }
        // 单份初评（排队、处理中超时）由 HomeworkAiReviewDispatcher 按 edu.ai-review.* 配置扫描处理。
    }

    private static boolean isStale(LocalDateTime created, LocalDateTime now, long seconds) {
        return created != null && created.plusSeconds(seconds).isBefore(now);
    }

    private static LocalDateTime lastActivity(LocalDateTime updated, LocalDateTime created) {
        return updated != null ? updated : created;
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static int remaining(AiEduReviewTaskDO task) {
        return Math.max(0, value(task.getTotalCount()) - value(task.getSuccessCount()) - value(task.getFailCount()));
    }
}
