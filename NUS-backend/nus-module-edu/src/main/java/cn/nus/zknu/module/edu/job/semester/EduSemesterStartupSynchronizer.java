package cn.nus.zknu.module.edu.job.semester;

import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成后立即校准一次当前学期。
 */
@Component
@Slf4j
public class EduSemesterStartupSynchronizer {

    @Resource
    private EduSemesterService eduSemesterService;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronize() {
        try {
            String result = eduSemesterService.autoUpdateCurrentSemester();
            log.info("[synchronize][启动时校准当前学期完成] {}", result);
        } catch (Exception e) {
            log.error("[synchronize][启动时校准当前学期失败，等待定时任务重试]", e);
        }
    }
}
