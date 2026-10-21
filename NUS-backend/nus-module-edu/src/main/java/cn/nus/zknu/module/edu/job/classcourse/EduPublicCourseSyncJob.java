package cn.nus.zknu.module.edu.job.classcourse;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** 每日补齐在线公共课的当前学期班课关联。 */
@Component
public class EduPublicCourseSyncJob implements JobHandler {

    @Resource
    private EduPublicCourseSyncService syncService;

    @Override
    public String execute(String param) {
        syncService.syncAll();
        return "公共课同步完成";
    }
}
