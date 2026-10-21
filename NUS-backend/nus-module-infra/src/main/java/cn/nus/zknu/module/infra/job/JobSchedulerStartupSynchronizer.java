package cn.nus.zknu.module.infra.job;

import cn.nus.zknu.module.infra.service.job.JobService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps Quartz definitions aligned with the database-backed task controls.
 */
@Component
@Slf4j
public class JobSchedulerStartupSynchronizer {

    @Resource
    private JobService jobService;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronize() throws SchedulerException {
        jobService.syncJob();
        log.info("[synchronize][定时任务配置已同步到 Quartz]");
    }
}
