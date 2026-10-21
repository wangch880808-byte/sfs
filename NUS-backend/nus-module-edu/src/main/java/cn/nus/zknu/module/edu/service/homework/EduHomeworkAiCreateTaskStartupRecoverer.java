package cn.nus.zknu.module.edu.service.homework;

import jakarta.annotation.Resource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EduHomeworkAiCreateTaskStartupRecoverer {

    @Resource
    private EduHomeworkService eduHomeworkService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverTasks() {
        eduHomeworkService.recoverAiCreateHomeworkPreviewTasks();
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void recoverPendingTasks() {
        eduHomeworkService.recoverAiCreateHomeworkPreviewTasks();
    }

}
