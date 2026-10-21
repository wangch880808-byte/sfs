package cn.nus.zknu.module.capacity.service.indicator;

import cn.nus.zknu.module.capacity.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 提交后再清除树缓存，避免其它请求在事务提交前重新缓存旧指标。 */
@Service
public class CapacityIndicatorCacheInvalidator {

    @Resource
    @Lazy
    private CapacityIndicatorCacheInvalidator self;

    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self.evict();
                }
            });
        } else {
            self.evict();
        }
    }

    @CacheEvict(cacheNames = RedisKeyConstants.INDICATOR_TREE, allEntries = true)
    public void evict() {
    }
}
