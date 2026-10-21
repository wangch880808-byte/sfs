package cn.nus.zknu.module.infra.service.config;

import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.infra.api.config.ConfigApiImpl;
import cn.nus.zknu.module.infra.cache.RedisCacheTestSupport;
import cn.nus.zknu.module.infra.controller.admin.config.vo.ConfigSaveReqVO;
import cn.nus.zknu.module.infra.dal.dataobject.config.ConfigDO;
import cn.nus.zknu.module.infra.dal.mysql.config.ConfigMapper;
import cn.nus.zknu.module.infra.dal.redis.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigServiceCacheTest extends RedisCacheTestSupport {

    private ConfigService service;
    private ConfigApi api;
    private ConfigMapper mapper;
    private final Map<String, ConfigDO> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        startContext(Beans.class, ConfigServiceImpl.class, ConfigCacheService.class, ConfigApiImpl.class);
        service = context.getBean(ConfigService.class);
        api = context.getBean(ConfigApi.class);
        mapper = context.getBean(ConfigMapper.class);
        when(mapper.selectByKey(any())).thenAnswer(i -> rows.get(i.getArgument(0)));
        when(mapper.selectById(anyLong())).thenAnswer(i -> rows.values().stream()
                .filter(row -> Objects.equals(row.getId(), i.getArgument(0))).findFirst().orElse(null));
        when(mapper.insert(any(ConfigDO.class))).thenAnswer(i -> {
            ConfigDO row = i.getArgument(0);
            row.setId(10L + rows.size());
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(mapper.updateById(any(ConfigDO.class))).thenAnswer(i -> {
            ConfigDO row = i.getArgument(0);
            rows.values().removeIf(old -> Objects.equals(old.getId(), row.getId()));
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(mapper.deleteById(anyLong())).thenAnswer(i -> {
            rows.values().removeIf(row -> Objects.equals(row.getId(), i.getArgument(0)));
            return 1;
        });
        when(mapper.selectByIds(anyCollection())).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(mapper.deleteByIds(anyCollection())).thenAnswer(i -> {
            rows.clear();
            return 1;
        });
    }

    @Test
    void serviceAndApiShareCacheAndRoundTripCompleteDo() {
        ConfigDO row = row("key", "初始值");
        row.setCreateTime(LocalDateTime.of(2026, 10, 10, 12, 0));
        row.setVisible(false);
        rows.put("key", row);

        assertEquals(row, service.getConfigByKey("key"));
        assertEquals(row, service.getConfigByKey("key"));
        assertEquals("初始值", api.getConfigValueByKey("key"));
        verify(mapper, times(1)).selectByKey("key");
        assertTtl(RedisKeyConstants.CONFIG_BY_KEY, Duration.ofSeconds(60));
        advance(Duration.ofSeconds(61));
        assertEquals("初始值", api.getConfigValueByKey("key"));
        verify(mapper, times(2)).selectByKey("key");
    }

    @Test
    void missingKeysAreCachedEvenWhenNullCachingIsDisabledAndCreateEvicts() {
        assertNull(service.getConfigByKey("missing"));
        assertNull(api.getConfigValueByKey("missing"));
        verify(mapper, times(1)).selectByKey("missing");

        service.createConfig(request(null, "missing", "新增值"));

        assertEquals("新增值", api.getConfigValueByKey("missing"));
        assertNull(service.getConfigByKey(null));
    }

    @Test
    void updateEvictsBothOldAndNewKeysWithoutEvictingOtherKeys() {
        rows.put("old", row("old", "旧值"));
        ConfigDO other = row("other", "不变");
        other.setId(2L);
        rows.put("other", other);
        assertEquals("旧值", api.getConfigValueByKey("old"));
        assertNull(api.getConfigValueByKey("new"));
        assertEquals("不变", api.getConfigValueByKey("other"));

        service.updateConfig(request(1L, "new", "新值"));

        assertNull(api.getConfigValueByKey("old"));
        assertEquals("新值", api.getConfigValueByKey("new"));
        assertEquals("不变", api.getConfigValueByKey("other"));
        verify(mapper, times(1)).selectByKey("other");
    }

    @Test
    void updateSameKeyAndSingleAndBatchDeleteRefreshReads() {
        rows.put("key", row("key", "旧值"));
        assertEquals("旧值", api.getConfigValueByKey("key"));
        service.updateConfig(request(1L, "key", "新值"));
        assertEquals("新值", api.getConfigValueByKey("key"));
        rows.get("key").setType(2);
        service.deleteConfig(1L);
        assertNull(api.getConfigValueByKey("key"));

        rows.put("batch", row("batch", "批量值"));
        assertEquals("批量值", api.getConfigValueByKey("batch"));
        service.deleteConfigList(List.of(1L));
        assertNull(api.getConfigValueByKey("batch"));
    }

    @Test
    void rejectedDeleteKeepsExistingValue() {
        ConfigDO row = row("system", "内置值");
        row.setType(1);
        rows.put("system", row);
        assertEquals("内置值", api.getConfigValueByKey("system"));
        assertThrows(RuntimeException.class, () -> service.deleteConfig(1L));
        assertEquals("内置值", api.getConfigValueByKey("system"));
        verify(mapper, times(1)).selectByKey("system");
    }

    @Test
    void directlyConstructedServiceKeepsOriginalReadAndWriteBehavior() {
        ConfigServiceImpl direct = new ConfigServiceImpl();
        ReflectionTestUtils.setField(direct, "configMapper", mapper);
        direct.createConfig(request(null, "plain", "原行为"));
        assertEquals("原行为", direct.getConfigByKey("plain").getValue());
        assertNull(direct.getConfigByKey("missing"));
    }

    private ConfigDO row(String key, String value) {
        ConfigDO row = new ConfigDO();
        row.setId(1L);
        row.setConfigKey(key);
        row.setValue(value);
        row.setType(2);
        return row;
    }

    private ConfigSaveReqVO request(Long id, String key, String value) {
        ConfigSaveReqVO req = new ConfigSaveReqVO();
        req.setId(id);
        req.setKey(key);
        req.setValue(value);
        req.setCategory("edu");
        req.setName(key);
        req.setVisible(true);
        return req;
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        ConfigMapper configMapper() {
            return mock(ConfigMapper.class);
        }
    }
}
