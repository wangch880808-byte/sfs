package cn.nus.zknu.module.edu.job.homework;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRemindService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 教师新提交汇总 Job（每 10 分钟检查，由任务中心触发）：同一任务最多每 edu.notify.teacher-digest-minutes 分钟
 * 通知一次发布教师有多少份新提交待批改；配置为 0 时跳过。
 */
@Component
@Slf4j
public class EduHomeworkTeacherDigestJob implements JobHandler {

    @Resource
    private EduHomeworkRemindService remindService;

    @Override
    public String execute(String param) {
        String result = remindService.sendTeacherDigests();
        log.info("[execute][教师新提交汇总完成] {}", result);
        return result;
    }

}
