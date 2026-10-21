package cn.nus.zknu.module.edu.dal.redis;

/** edu 模块缓存名称，过期时间由现有 Redis 缓存管理器解析。 */
public interface RedisKeyConstants {

    String CURRENT_SEMESTER = "edu_current_semester#10m";
    String SEMESTER_OPTIONS = "edu_semester_options#60s";

}
