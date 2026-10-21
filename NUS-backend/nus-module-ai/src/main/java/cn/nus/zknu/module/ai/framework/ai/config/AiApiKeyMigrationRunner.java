package cn.nus.zknu.module.ai.framework.ai.config;

import cn.nus.zknu.module.ai.service.model.AiApiKeyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 将升级前的 AI API 明文密钥迁移为加密存储。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class AiApiKeyMigrationRunner implements ApplicationRunner {

    @Resource
    private AiApiKeyService apiKeyService;

    @Override
    public void run(ApplicationArguments args) {
        int migrated = apiKeyService.migratePlaintextApiKeys();
        if (migrated > 0) {
            log.info("[run][已完成 {} 个 AI API 密钥的加密迁移]", migrated);
        }
    }

}
