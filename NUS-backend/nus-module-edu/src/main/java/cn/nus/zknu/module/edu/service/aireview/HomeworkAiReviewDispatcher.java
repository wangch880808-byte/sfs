package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewPolicy;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * AI 初评的派发器（发件箱模式）。
 * <ul>
 *     <li>提交事务里写入「排队中」记录，事务提交后才把记录派发到线程池；</li>
 *     <li>线程池拒绝时记录保持「排队中」，由定时扫描再次派发；</li>
 *     <li>执行前以「排队中 → 处理中」的条件更新认领，多实例或重复派发只会有一个成功；</li>
 *     <li>定时扫描同时把排队过久、处理过久的记录判为失败，并通知布置教师；</li>
 *     <li>服务启动时把上次运行遗留的「处理中」记录放回排队续跑（可用配置关闭）。</li>
 * </ul>
 * 扫描间隔与每批数量来自 infra_config（edu.ai-review.*），每次扫描后重新读取。
 */
@Component
@Slf4j
public class HomeworkAiReviewDispatcher implements SchedulingConfigurer {

    /** 每次扫描最多处理的过期记录数，避免一次扫描过长。 */
    static final int STALE_SCAN_LIMIT = 200;

    @Resource(name = "eduAiReviewTaskExecutor")
    private Executor executor;
    @Resource
    private EduHomeworkAiReviewMapper reviewMapper;
    @Resource
    @Lazy
    private EduHomeworkAiReviewService reviewService;
    @Resource
    private EduAiReviewPolicy policy;
    @Resource
    private HomeworkAiReviewFailureNotifier failureNotifier;

    /** 启动续跑每次取的处理中记录数。 */
    static final int REQUEUE_PAGE_SIZE = 200;

    /** 本实例已交给线程池、尚未执行完的初评，避免同一条被重复塞进队列。 */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 本实例开始创建的时间。早于它最后更新的「处理中」记录只可能来自上一次运行；
     * 减 1 秒是因为 update_time 只精确到秒（MySQL 会四舍五入），避免误伤本实例刚认领的记录。
     */
    private final LocalDateTime instanceStartedAt = LocalDateTime.now().withNano(0).minusSeconds(1);

    /**
     * 服务启动后把上次运行遗留的「处理中」初评放回排队并立即派发，受 edu.ai-review.requeue-on-startup 控制。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            requeueOnStartup();
        } catch (Exception ex) {
            log.error("[requeueOnStartup][AI 初评启动续跑异常，遗留记录将由超时判为失败]", ex);
        }
    }

    /** @return 放回排队的条数 */
    int requeueOnStartup() {
        if (!policy.requeueOnStartup()) {
            log.info("[requeueOnStartup][启动续跑已关闭（{}=false），遗留的处理中 AI 初评将由超时判为失败]",
                    EduAiReviewPolicy.REQUEUE_ON_STARTUP);
            return 0;
        }
        int requeued = 0;
        while (true) {
            List<EduHomeworkAiReviewDO> leftovers = reviewMapper.selectStale(
                    HomeworkAiReviewStatusEnum.PROCESSING, instanceStartedAt, REQUEUE_PAGE_SIZE);
            int changed = 0;
            for (EduHomeworkAiReviewDO review : leftovers) {
                if (inFlight.contains(review.getId())) {
                    continue;
                }
                changed += reviewMapper.requeueProcessing(review.getId(), review.getGenerationToken(),
                        UUID.randomUUID().toString());
            }
            requeued += changed;
            // 本页一条都没改动（全被别处改走或在本实例执行中）或已取完时结束，避免死循环。
            if (changed == 0 || leftovers.size() < REQUEUE_PAGE_SIZE) {
                break;
            }
        }
        log.info("[requeueOnStartup][服务启动，{} 条处理中的 AI 初评已放回排队]", requeued);
        if (requeued > 0) {
            dispatchQueued();
        }
        return requeued;
    }

    /**
     * 有事务时在提交之后派发，没有事务时立即派发。事务回滚则不派发，排队记录也随之回滚。
     */
    public void dispatchAfterCommit(Long reviewId) {
        if (reviewId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(reviewId);
                }
            });
        } else {
            dispatch(reviewId);
        }
    }

    /**
     * 把排队中的初评交给线程池。
     *
     * @return false 表示线程池已满，记录保持排队中，等下次扫描
     */
    public boolean dispatch(Long reviewId) {
        if (!inFlight.add(reviewId)) {
            return true;
        }
        try {
            executor.execute(() -> {
                try {
                    reviewService.processQueuedReview(reviewId);
                } catch (Throwable ex) {
                    log.error("[dispatch][AI 初评执行异常 reviewId={}]", reviewId, ex);
                } finally {
                    inFlight.remove(reviewId);
                }
            });
            return true;
        } catch (RejectedExecutionException ex) {
            inFlight.remove(reviewId);
            log.warn("[dispatch][AI 初评线程池已满，保持排队等待下次扫描 reviewId={}]", reviewId);
            return false;
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::pollSafely, nextRunTrigger());
    }

    Trigger nextRunTrigger() {
        return context -> {
            Instant last = context.lastCompletion();
            return (last != null ? last : Instant.now()).plusSeconds(policy.dispatchIntervalSeconds());
        };
    }

    void pollSafely() {
        try {
            poll();
        } catch (Exception ex) {
            log.error("[poll][AI 初评派发扫描异常]", ex);
        }
    }

    /** 一次扫描：先关闭过期记录，再按入队先后派发排队记录。 */
    public void poll() {
        failStaleQueued();
        failStaleProcessing();
        dispatchQueued();
    }

    int dispatchQueued() {
        List<EduHomeworkAiReviewDO> queued = reviewMapper.selectQueued(policy.dispatchBatchSize());
        int dispatched = 0;
        for (EduHomeworkAiReviewDO review : queued) {
            if (inFlight.contains(review.getId())) {
                continue;
            }
            if (!dispatch(review.getId())) {
                break;
            }
            dispatched++;
        }
        return dispatched;
    }

    void failStaleQueued() {
        int minutes = policy.queueTimeoutMinutes();
        LocalDateTime before = LocalDateTime.now().minusMinutes(minutes);
        String cause = HomeworkAiReviewFailures.queueOverflowCause(minutes);
        for (EduHomeworkAiReviewDO review : reviewMapper.selectStale(
                HomeworkAiReviewStatusEnum.QUEUED, before, STALE_SCAN_LIMIT)) {
            EduHomeworkAiReviewDO failed = HomeworkAiReviewFailures.failed(review.getId(), cause);
            if (reviewMapper.failIfQueued(failed, review.getId(), review.getGenerationToken()) == 1) {
                log.warn("[failStaleQueued][AI 初评排队超时 reviewId={}]", review.getId());
                failureNotifier.notifyFailure(review.getHomeworkId(), cause);
            }
        }
    }

    void failStaleProcessing() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(policy.processingTimeoutSeconds());
        String cause = HomeworkAiReviewFailures.PROCESSING_TIMEOUT_CAUSE;
        for (EduHomeworkAiReviewDO review : reviewMapper.selectStale(
                HomeworkAiReviewStatusEnum.PROCESSING, before, STALE_SCAN_LIMIT)) {
            EduHomeworkAiReviewDO failed = HomeworkAiReviewFailures.failed(review.getId(), cause);
            if (reviewMapper.updateIfProcessing(failed, review.getId(), review.getGenerationToken()) == 1) {
                log.warn("[failStaleProcessing][AI 初评处理超时 reviewId={}]", review.getId());
                failureNotifier.notifyFailure(review.getHomeworkId(), cause);
            }
        }
    }

    /** 仅供测试观察。 */
    boolean isInFlight(Long reviewId) {
        return inFlight.contains(reviewId);
    }
}
