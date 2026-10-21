package cn.nus.zknu.module.edu.service.classcourse;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 提交后异步同步；同一范围只保留一个排队任务，执行中的新变更合并为下一轮。 */
@Component
@Slf4j
public class EduPublicCourseSyncScheduler {

    @Resource
    @Lazy
    private EduPublicCourseSyncService syncService;
    @Resource(name = "eduPublicCourseSyncExecutor")
    private Executor executor;

    private final Map<Scope, State> scheduled = new HashMap<>();

    public void requestAll() {
        afterCommit(() -> submit(new Scope("all", null), syncService::syncAll));
    }

    public void requestClass(Long classId) {
        afterCommit(() -> submit(new Scope("class", classId), () -> syncService.syncClass(classId)));
    }

    /** 课程事件监听器已经处于 AFTER_COMMIT，直接投递，避免再次注册提交回调。 */
    public void submitCourse(Long courseId) {
        submit(new Scope("course", courseId), () -> syncService.syncCourse(courseId));
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void submit(Scope scope, Runnable task) {
        State state;
        synchronized (scheduled) {
            State existing = scheduled.get(scope);
            if (existing != null) {
                // 已排队的任务会读取最新状态；执行中收到的提交必须再读取一轮，不能丢掉下线等变更。
                existing.rerun |= existing.running;
                return;
            }
            state = new State();
            scheduled.put(scope, state);
        }
        try {
            executor.execute(() -> run(scope, state, task));
        } catch (RejectedExecutionException ex) {
            synchronized (scheduled) {
                scheduled.remove(scope, state);
            }
            // 不在 HTTP 线程执行。每日同步补齐关联，backfill_pending 保留失败补发批次。
            log.warn("[submit][公共课同步队列已满或关闭，交由每日同步重试 scope={}]", scope);
        }
    }

    private void run(Scope scope, State state, Runnable task) {
        synchronized (scheduled) {
            state.running = true;
        }
        while (true) {
            long started = System.nanoTime();
            log.info("[run][公共课后台同步开始 scope={}]", scope);
            try {
                task.run();
            } catch (Exception ex) {
                log.error("[run][公共课后台同步失败，交由每日同步重试 scope={}]", scope, ex);
            } finally {
                log.info("[run][公共课后台同步结束 scope={}, durationMs={}]", scope,
                        (System.nanoTime() - started) / 1_000_000);
            }
            synchronized (scheduled) {
                if (!state.rerun) {
                    scheduled.remove(scope, state);
                    return;
                }
                state.rerun = false;
            }
        }
    }

    private record Scope(String type, Long id) {
    }

    private static class State {
        private boolean running;
        private boolean rerun;
    }
}
