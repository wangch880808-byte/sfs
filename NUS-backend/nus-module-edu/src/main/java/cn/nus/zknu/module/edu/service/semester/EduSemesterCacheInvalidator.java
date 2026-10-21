package cn.nus.zknu.module.edu.service.semester;

import cn.nus.zknu.module.edu.dal.redis.RedisKeyConstants;
import cn.nus.zknu.module.infra.service.config.ConfigChangedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 参数配置页面和学期管理均可改动学期配置，需要清除同一份快照。 */
@Component
public class EduSemesterCacheInvalidator {

    @EventListener
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisKeyConstants.CURRENT_SEMESTER, key = "'current'",
                    condition = "#event.key != null && #event.key.startsWith('edu.current.semester.')"),
            @CacheEvict(cacheNames = RedisKeyConstants.SEMESTER_OPTIONS, allEntries = true,
                    condition = "#event.key != null && #event.key.startsWith('edu.current.semester.')")
    })
    public void onConfigChanged(ConfigChangedEvent event) {
    }

    @CacheEvict(cacheNames = RedisKeyConstants.SEMESTER_OPTIONS, allEntries = true)
    public void evictOptions() {
    }
}
