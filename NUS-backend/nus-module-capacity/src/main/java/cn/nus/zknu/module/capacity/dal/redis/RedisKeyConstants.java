package cn.nus.zknu.module.capacity.dal.redis;

/** capacity 模块缓存名称，过期时间由现有 Redis 缓存管理器解析。 */
public interface RedisKeyConstants {

    String INDICATOR_TREE = "capacity_indicator_tree#1h";

}
