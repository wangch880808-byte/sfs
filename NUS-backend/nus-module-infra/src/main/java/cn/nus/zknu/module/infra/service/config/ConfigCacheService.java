package cn.nus.zknu.module.infra.service.config;

import cn.nus.zknu.module.infra.dal.dataobject.config.ConfigDO;
import cn.nus.zknu.module.infra.dal.mysql.config.ConfigMapper;
import cn.nus.zknu.module.infra.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 独立代理避免服务内部调用绕过缓存，同时保留缺失配置的负缓存。 */
@Service
public class ConfigCacheService {

    @Resource
    private ConfigMapper configMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Cacheable(cacheNames = RedisKeyConstants.CONFIG_BY_KEY, key = "#key", condition = "#key != null")
    public List<ConfigDO> getConfigByKey(String key) {
        ConfigDO config = configMapper.selectByKey(key);
        // 空集合可被现有 JSON 序列化器保存，也不依赖 cache-null-values 的部署配置。
        List<ConfigDO> result = new ArrayList<>();
        if (config != null) {
            result.add(config);
        }
        return result;
    }

    @CacheEvict(cacheNames = RedisKeyConstants.CONFIG_BY_KEY, key = "#key", condition = "#key != null")
    public void evict(String key) {
        eventPublisher.publishEvent(new ConfigChangedEvent(key));
    }
}
