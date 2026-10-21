package cn.nus.zknu.module.infra.job.logger;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.infra.job.LogCleanJobUtils;
import cn.nus.zknu.module.infra.service.logger.ApiErrorLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 物理删除 N 天前的错误日志的 Job
 *
 * @author hyh
 */
@Slf4j
@Component
public class ErrorLogCleanJob implements JobHandler {

    /**
     * 默认保留（180）天的日志（参照等保要求日志留存不少于 6 个月）；可通过任务参数指定保留天数，例如 "180"
     */
    static final int DEFAULT_RETAIN_DAYS = 180;
    /**
     * 每次删除间隔的条数，如果值太高可能会造成数据库的压力过大
     */
    private static final Integer DELETE_LIMIT = 100;
    @Resource
    private ApiErrorLogService apiErrorLogService;

    @Override
    public String execute(String param) {
        int retainDays = LogCleanJobUtils.parseRetainDays(param, DEFAULT_RETAIN_DAYS);
        Integer count = apiErrorLogService.cleanErrorLog(retainDays, DELETE_LIMIT);
        log.info("[execute][定时执行清理错误日志数量 ({}) 个，保留 ({}) 天]", count, retainDays);
        return String.format("定时执行清理错误日志数量 %s 个，保留 %s 天", count, retainDays);
    }

}
