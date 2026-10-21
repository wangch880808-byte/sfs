package cn.nus.zknu.module.infra.dal.redis;

/** infra 模块缓存名称，过期时间由现有 Redis 缓存管理器解析。 */
public interface RedisKeyConstants {

    String CONFIG_BY_KEY = "infra_config_by_key#60s";

}
