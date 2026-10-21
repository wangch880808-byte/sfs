package cn.nus.zknu.module.edu.job.semester;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 当前学期自动更新 Job（由任务中心触发，不使用 @Scheduled）。
 */
@Component
@Slf4j
public class EduSemesterAutoUpdateJob implements JobHandler {

    @Resource
    private EduSemesterService eduSemesterService;

    @Override
    public String execute(String param) {
        String result = eduSemesterService.autoUpdateCurrentSemester();
        log.info("[execute][当前学期自动更新完成] {}", result);
        return result;
    }
}
