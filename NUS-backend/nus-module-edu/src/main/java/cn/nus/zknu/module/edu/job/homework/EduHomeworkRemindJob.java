package cn.nus.zknu.module.edu.job.homework;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRemindService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 任务截止前提醒 Job（每小时，由任务中心触发）：实际截止时间在 edu.remind.before-hours 小时内、
 * 还没提交的学生，每人每个任务只提醒一次；edu.remind.enabled 为 false 时跳过。
 */
@Component
@Slf4j
public class EduHomeworkRemindJob implements JobHandler {

    @Resource
    private EduHomeworkRemindService remindService;

    @Override
    public String execute(String param) {
        String result = remindService.remindBeforeDeadline();
        log.info("[execute][任务截止前提醒完成] {}", result);
        return result;
    }

}
