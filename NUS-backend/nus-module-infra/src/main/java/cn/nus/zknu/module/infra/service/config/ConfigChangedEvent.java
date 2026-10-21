package cn.nus.zknu.module.infra.service.config;

/** 配置写入完成后通知使用整组配置的业务缓存。 */
public record ConfigChangedEvent(String key) {
}
