package cn.nus.zknu.module.capacity.service.indicator;

import cn.nus.zknu.module.capacity.cache.RedisCacheTestSupport;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorBatchCreateReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorResetWeightReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import cn.nus.zknu.module.capacity.dal.mysql.indicator.CapacityIndicatorMapper;
import cn.nus.zknu.module.capacity.dal.redis.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CapacityIndicatorCacheTest extends RedisCacheTestSupport {

    private CapacityIndicatorService service;
    private CapacityIndicatorMapper mapper;
    private final Map<Long, CapacityIndicatorDO> rows = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        startContext(Beans.class, CapacityIndicatorServiceImpl.class, CapacityIndicatorCacheInvalidator.class);
        service = context.getBean(CapacityIndicatorService.class);
        mapper = context.getBean(CapacityIndicatorMapper.class);
        when(mapper.selectListForTree()).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(mapper.selectListByIndicatorLevel(1)).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(mapper.selectById(anyLong())).thenAnswer(i -> rows.get(i.getArgument(0)));
        when(mapper.insert(any(CapacityIndicatorDO.class))).thenAnswer(i -> {
            CapacityIndicatorDO row = i.getArgument(0);
            row.setId(1L + rows.size());
            rows.put(row.getId(), row);
            return 1;
        });
        when(mapper.updateById(any(CapacityIndicatorDO.class))).thenAnswer(i -> {
            CapacityIndicatorDO updated = i.getArgument(0);
            CapacityIndicatorDO row = rows.get(updated.getId());
            row.setWeight(updated.getWeight());
            if (updated.getIndicatorName() != null) {
                row.setIndicatorName(updated.getIndicatorName());
            }
            return 1;
        });
        when(mapper.deleteById(anyLong())).thenAnswer(i -> {
            rows.remove(i.getArgument(0));
            return 1;
        });
    }

    @Test
    void emptyTreeAndPopulatedTreeCacheWithOneHourTtlAndSerialization() {
        assertTrue(service.getIndicatorList().isEmpty());
        assertTrue(service.getIndicatorList().isEmpty());
        verify(mapper, times(1)).selectListForTree();
        assertTtl(RedisKeyConstants.INDICATOR_TREE, Duration.ofHours(1));
        advance(Duration.ofHours(2));
        assertTrue(service.getIndicatorList().isEmpty());
        verify(mapper, times(2)).selectListForTree();
        service.createIndicator(request(null, "指标", "20"));
        var first = service.getIndicatorList();
        assertEquals(first, service.getIndicatorList());
        assertEquals(new BigDecimal("20"), service.getIndicatorList().get(0).getWeight());
        verify(mapper, times(3)).selectListForTree();
    }

    @Test
    void createUpdateAndDeleteEvictTree() {
        service.getIndicatorList();
        Long id = service.createIndicator(request(null, "初始指标", "20"));
        assertEquals("初始指标", service.getIndicatorList().get(0).getIndicatorName());
        service.updateIndicator(request(id, "修改指标", "30"));
        assertEquals("修改指标", service.getIndicatorList().get(0).getIndicatorName());
        assertEquals(new BigDecimal("30"), service.getIndicatorList().get(0).getWeight());
        service.deleteIndicator(id);
        assertTrue(service.getIndicatorList().isEmpty());
        verify(mapper, times(4)).selectListForTree();
    }

    @Test
    void batchCreationAndResetWeightsEvictTree() {
        service.getIndicatorList();
        CapacityIndicatorBatchCreateReqVO request = new CapacityIndicatorBatchCreateReqVO();
        request.setIndicatorLevel(1);
        request.setParentIndicatorId(0L);
        var item = new CapacityIndicatorBatchCreateReqVO.IndicatorItem();
        item.setIndicatorName("批量指标");
        item.setWeight(new BigDecimal("20"));
        item.setStatus(0);
        request.setIndicators(List.of(item));
        service.batchCreateIndicators(request);
        assertEquals(new BigDecimal("20"), service.getIndicatorList().get(0).getWeight());

        CapacityIndicatorResetWeightReqVO reset = new CapacityIndicatorResetWeightReqVO();
        reset.setIndicatorLevel(1);
        reset.setParentIndicatorId(0L);
        var weight = new CapacityIndicatorResetWeightReqVO.IndicatorWeightItem();
        weight.setId(1L);
        weight.setWeight(new BigDecimal("50"));
        reset.setIndicators(List.of(weight));
        service.resetIndicatorWeights(reset);
        assertEquals(new BigDecimal("50"), service.getIndicatorList().get(0).getWeight());
        verify(mapper, times(3)).selectListForTree();
    }

    @Test
    void rejectedUpdateKeepsTreeAndOriginalValidation() {
        Long id = service.createIndicator(request(null, "指标", "20"));
        var before = service.getIndicatorList();
        assertThrows(RuntimeException.class, () -> service.updateIndicator(request(id, "指标", "101")));
        assertEquals(before, service.getIndicatorList());
        verify(mapper, times(1)).selectListForTree();
        verify(mapper, never()).updateById(any(CapacityIndicatorDO.class));
    }

    @Test
    void treeEvictionWaitsForTransactionCommitAndRollbackKeepsCachedSnapshot() {
        service.createIndicator(request(null, "原指标", "20"));
        assertEquals("原指标", service.getIndicatorList().get(0).getIndicatorName());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.updateIndicator(request(1L, "新指标", "30"));
            assertEquals("原指标", service.getIndicatorList().get(0).getIndicatorName());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertEquals("新指标", service.getIndicatorList().get(0).getIndicatorName());
        } finally {
            TransactionSynchronizationManager.clear();
        }
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.updateIndicator(request(1L, "回滚指标", "40"));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertEquals("新指标", service.getIndicatorList().get(0).getIndicatorName());
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verify(mapper, times(2)).selectListForTree();
    }

    private CapacityIndicatorSaveReqVO request(Long id, String name, String weight) {
        CapacityIndicatorSaveReqVO request = new CapacityIndicatorSaveReqVO();
        request.setId(id);
        request.setIndicatorName(name);
        request.setIndicatorLevel(1);
        request.setParentIndicatorId(0L);
        request.setWeight(new BigDecimal(weight));
        request.setStatus(0);
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        CapacityIndicatorMapper capacityIndicatorMapper() {
            return mock(CapacityIndicatorMapper.class);
        }
    }
}
