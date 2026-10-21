package cn.nus.zknu.module.edu.service.homework;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 批改、打回的学生通知：撤销期过后发送。
 * <p>
 * 撤销期按秒计，任务中心的 Cron 粒度和人工开关都不合适，这里和 AI 初评恢复任务一样用 Spring 定时；
 * 待发送的通知存在 edu_homework_grading_action，服务重启后照常补发，多实例时靠条件更新保证只发一次。
 */
@Component
@Slf4j
public class EduGradingNotifySender {

    @Resource
    private EduHomeworkGradingActionService gradingActionService;

    @Scheduled(fixedDelay = 5_000L, initialDelay = 20_000L)
    public void sendDueNotifications() {
        try {
            int sent = gradingActionService.sendDueNotifications();
            if (sent > 0) {
                log.info("[sendDueNotifications][发出批改通知 {} 条]", sent);
            }
        } catch (Exception ex) {
            log.warn("[sendDueNotifications][批改通知发送任务失败，下一轮重试]", ex);
        }
    }
}
