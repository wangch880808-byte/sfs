package cn.nus.zknu.module.ai.job;

import cn.nus.zknu.framework.quartz.core.handler.JobHandler;
import cn.nus.zknu.module.ai.framework.ai.core.governance.AiGovernanceConfig;
import cn.nus.zknu.module.ai.service.log.AiCallLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 物理删除超过保留天数的 AI 调用日志；保留天数读参数配置 ai.call-log.retention-days（默认 180）。
 */
@Component
@Slf4j
public class AiCallLogCleanJob implements JobHandler {

    @Resource
    private AiCallLogService callLogService;
    @Resource
    private AiGovernanceConfig governanceConfig;

    @Override
    public String execute(String param) {
        int retentionDays = governanceConfig.callLogRetentionDays();
        int count = callLogService.cleanExpired(retentionDays);
        log.info("[execute][清理 AI 调用日志 {} 条，保留 {} 天]", count, retentionDays);
        return String.format("清理 AI 调用日志 %s 条，保留 %s 天", count, retentionDays);
    }

}
