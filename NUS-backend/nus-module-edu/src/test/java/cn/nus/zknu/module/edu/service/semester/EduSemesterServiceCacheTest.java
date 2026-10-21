package cn.nus.zknu.module.edu.service.semester;

import cn.nus.zknu.module.edu.cache.RedisCacheTestSupport;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduSemesterUsageDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.redis.RedisKeyConstants;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import cn.nus.zknu.module.infra.dal.dataobject.config.ConfigDO;
import cn.nus.zknu.module.infra.dal.mysql.config.ConfigMapper;
import cn.nus.zknu.module.infra.service.config.ConfigCacheService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduSemesterServiceCacheTest extends RedisCacheTestSupport {

    private EduSemesterService service;
    private EduSemesterOptionService options;
    private ConfigCacheService configCache;
    private DataSource dataSource;
    private EduClassCourseMapper mapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        startContext(Beans.class, EduSemesterServiceImpl.class, ConfigCacheService.class,
                EduSemesterCacheInvalidator.class, EduSemesterOptionService.class,
                cn.nus.zknu.module.edu.controller.admin.certificate.EduCertificateController.class);
        service = context.getBean(EduSemesterService.class);
        options = context.getBean(EduSemesterOptionService.class);
        configCache = context.getBean(ConfigCacheService.class);
        dataSource = context.getBean(DataSource.class);
        mapper = context.getBean(EduClassCourseMapper.class);
        jdbc = new JdbcTemplate(dataSource);
        when(mapper.selectSemesterUsage()).thenReturn(new ArrayList<>());
        when(context.getBean(ConfigMapper.class).selectByKey(anyString())).thenAnswer(i -> {
            List<ConfigDO> rows = jdbc.query("SELECT id, config_key, value FROM infra_config WHERE config_key = ? AND deleted = 0",
                    (rs, n) -> {
                        ConfigDO row = new ConfigDO();
                        row.setId(rs.getLong("id"));
                        row.setConfigKey(rs.getString("config_key"));
                        row.setValue(rs.getString("value"));
                        return row;
                    }, (String) i.getArgument(0));
            return rows.isEmpty() ? null : rows.get(0);
        });
        clearInvocations(dataSource);
    }

    @Test
    void currentSemesterCachesFourJdbcReadsIncludingUnsetDefaultAndExpires() throws Exception {
        assertEquals("未设置", service.getCurrentSemester().getSemesterName());
        assertNull(service.getCurrentSemester().getSemesterStartDate());
        assertFalse(service.getCurrentSemester().getAutoEnabled());
        verify(dataSource, times(4)).getConnection();
        assertTtl(RedisKeyConstants.CURRENT_SEMESTER, Duration.ofMinutes(10));
        advance(Duration.ofMinutes(11));
        assertEquals("未设置", service.getCurrentSemester().getSemesterName());
        verify(dataSource, times(8)).getConnection();
    }

    @Test
    void manualSaveClearsMissingConfigAndCurrentCacheBeforeBackgroundSync() {
        assertEquals("未设置", service.getCurrentSemester().getSemesterName());
        assertTrue(configCache.getConfigByKey("edu.current.semester.name").isEmpty());
        doAnswer(i -> {
            assertEquals("手工学期", service.getCurrentSemester().getSemesterName());
            assertFalse(service.getCurrentSemester().getAutoEnabled());
            return null;
        }).when(context.getBean(EduPublicCourseSyncScheduler.class)).requestAll();

        service.saveCurrentSemester(request(" 手工学期 "));

        assertEquals("手工学期", service.getCurrentSemester().getSemesterName());
        assertEquals("手工学期", configCache.getConfigByKey("edu.current.semester.name").get(0).getValue());
        assertEquals(LocalDate.of(2026, 8, 1), service.getCurrentSemester().getSemesterStartDate());
    }

    @Test
    void directParameterChangeInvalidatesCurrentSemesterSnapshot() {
        service.saveCurrentSemester(request("旧学期"));
        assertEquals("旧学期", service.getCurrentSemester().getSemesterName());
        jdbc.update("UPDATE infra_config SET value = '新学期' WHERE config_key = 'edu.current.semester.name'");
        configCache.evict("unrelated.key");
        assertEquals("旧学期", service.getCurrentSemester().getSemesterName());
        configCache.evict("edu.current.semester.name");
        assertEquals("新学期", service.getCurrentSemester().getSemesterName());
    }

    @Test
    void automationReadsRealLockedConfigAndEvictsAfterChangingSemester() {
        service.saveCurrentSemester(request("手工学期"));
        assertFalse(service.getCurrentSemester().getAutoEnabled());
        service.setAutoEnabled(true);
        String expected = EduSemesterServiceImpl.calculateAutomaticSemester(LocalDate.now()).semesterName();
        assertEquals(expected, service.getCurrentSemester().getSemesterName());
        assertTrue(service.getCurrentSemester().getAutoEnabled());

        jdbc.update("UPDATE infra_config SET value = '往期学期' WHERE config_key = 'edu.current.semester.name'");
        configCache.evict("edu.current.semester.name");
        assertEquals("往期学期", service.getCurrentSemester().getSemesterName());
        assertEquals("已更新为" + expected, service.autoUpdateCurrentSemester());
        assertEquals(expected, service.getCurrentSemester().getSemesterName());
        assertEquals("当前已是" + expected + "，无需更新", service.autoUpdateCurrentSemester());
        service.setAutoEnabled(false);
        assertFalse(service.getCurrentSemester().getAutoEnabled());
        assertEquals("自动更新已关闭，保留当前手动设置", service.autoUpdateCurrentSemester());
    }

    @Test
    void optionsCacheOnOriginalServiceKeepsOrderCountsAndRefreshesWithinOneMinute() {
        service.saveCurrentSemester(request("2026-2027 上学期"));
        when(mapper.selectSemesterUsage()).thenReturn(new ArrayList<>(List.of(
                usage("2024-2025 下学期", 2), usage("2026-2027 上学期", 3), usage("2025-2026 上学期", 4))));
        var first = options.listOptions();
        var second = options.listOptions();
        assertEquals(first, second);
        assertEquals(List.of("2026-2027 上学期", "2025-2026 上学期", "2024-2025 下学期"),
                second.stream().map(o -> o.getSemesterName()).toList());
        assertEquals(3, second.get(0).getClassCourseCount());
        verify(mapper, times(1)).selectSemesterUsage();
        assertTtl(RedisKeyConstants.SEMESTER_OPTIONS, Duration.ofSeconds(60));

        when(mapper.selectSemesterUsage()).thenReturn(new ArrayList<>(List.of(usage("新增学期", 1))));
        assertEquals(3, options.listOptions().size());
        advance(Duration.ofSeconds(61));
        assertEquals("新增学期", options.listOptions().get(1).getSemesterName());
        verify(mapper, times(2)).selectSemesterUsage();
    }

    @Test
    void semesterSwitchUsesNewOptionsKeyAndResolvePreservesFreshValidation() {
        service.saveCurrentSemester(request("旧学期"));
        assertEquals("旧学期", options.listOptions().get(0).getSemesterName());
        service.saveCurrentSemester(request("新学期"));
        assertEquals("新学期", options.listOptions().get(0).getSemesterName());
        when(mapper.selectSemesterUsage()).thenReturn(new ArrayList<>(List.of(usage("刚绑定的学期", 1))));
        assertEquals("刚绑定的学期", options.resolve(" 刚绑定的学期 ").getSemesterName());
        assertEquals("新学期", options.resolve(null).getSemesterName());
    }

    @Test
    void transientReadFailurePreservesFallbackButRetriesImmediately() throws Exception {
        service.saveCurrentSemester(request("真实学期"));
        doThrow(new java.sql.SQLException("临时连接失败")).doCallRealMethod().when(dataSource).getConnection();
        var fallback = service.getCurrentSemester();
        assertEquals("未设置", fallback.getSemesterName());
        assertTrue(fallback.isConfigReadFailed());
        assertFalse(cn.nus.zknu.framework.common.util.json.JsonUtils.toJsonString(fallback).contains("configReadFailed"));
        assertEquals("真实学期", service.getCurrentSemester().getSemesterName());
        service.getCurrentSemester();
        verify(dataSource, times(9)).getConnection();
    }

    @Test
    void certificateWritesEvictMissingAndExistingValuesAfterCommit() throws Exception {
        var controller = context.getBean(cn.nus.zknu.module.edu.controller.admin.certificate.EduCertificateController.class);
        for (String key : List.of("edu.certificate.threshold", "edu.pass.score")) {
            assertTrue(configCache.getConfigByKey(key).isEmpty());
        }
        assertTrue(controller.setThreshold(81D).getData());
        assertTrue(controller.setPassScore(61D).getData());
        assertEquals("81.0", configCache.getConfigByKey("edu.certificate.threshold").get(0).getValue());
        assertEquals("61.0", configCache.getConfigByKey("edu.pass.score").get(0).getValue());
        assertTrue(controller.setThreshold(82D).getData());
        assertTrue(controller.setPassScore(62D).getData());
        assertEquals("82.0", configCache.getConfigByKey("edu.certificate.threshold").get(0).getValue());
        assertEquals("62.0", configCache.getConfigByKey("edu.pass.score").get(0).getValue());
        // 非学期事件不应清理当前学期与选项缓存。
        service.getCurrentSemester();
        options.listOptions();
        clearInvocations(dataSource, mapper, context.getBean(ConfigMapper.class));
        assertNotEquals(0, controller.setThreshold(-1D).getCode());
        assertNotEquals(0, controller.setPassScore(101D).getCode());
        doThrow(new java.sql.SQLException("保存连接失败")).when(dataSource).getConnection();
        assertEquals(500, controller.setThreshold(90D).getCode());
        assertEquals(500, controller.setPassScore(90D).getCode());
        assertEquals("82.0", configCache.getConfigByKey("edu.certificate.threshold").get(0).getValue());
        assertEquals("62.0", configCache.getConfigByKey("edu.pass.score").get(0).getValue());
        service.getCurrentSemester();
        options.listOptions();
        verifyNoInteractions(context.getBean(ConfigMapper.class), mapper);
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    void optionEvictionRefreshesArchivedCountsImmediately() {
        service.saveCurrentSemester(request("当前学期"));
        when(mapper.selectSemesterUsage()).thenReturn(new ArrayList<>(List.of(usage("当前学期", 2))));
        assertEquals(2, options.listOptions().get(0).getActiveClassCourseCount());
        var archived = usage("当前学期", 2);
        archived.setActiveLinkCount(0);
        when(mapper.selectSemesterUsage()).thenReturn(new ArrayList<>(List.of(archived)));
        assertEquals(2, options.listOptions().get(0).getActiveClassCourseCount());
        context.getBean(EduSemesterCacheInvalidator.class).evictOptions();
        assertEquals(0, options.listOptions().get(0).getActiveClassCourseCount());
        verify(mapper, times(2)).selectSemesterUsage();
    }

    @Test
    void failedSavePreservesCachedSemesterAndDoesNotQueueSync() {
        service.saveCurrentSemester(request("原学期"));
        assertEquals("原学期", service.getCurrentSemester().getSemesterName());
        clearInvocations(context.getBean(EduPublicCourseSyncScheduler.class));
        var invalid = request("新学期");
        invalid.setSemesterEndDate(LocalDate.of(2025, 1, 1));
        assertThrows(RuntimeException.class, () -> service.saveCurrentSemester(invalid));
        assertEquals("原学期", service.getCurrentSemester().getSemesterName());
        verifyNoInteractions(context.getBean(EduPublicCourseSyncScheduler.class));
    }

    @Override
    protected void registerAdditionalBeans() {
        // 调度器只验证提交与投递顺序，直接注册模拟对象避免注入真实后台线程依赖。
        context.getBeanFactory().registerSingleton("publicCourseSyncScheduler", mock(EduPublicCourseSyncScheduler.class));
    }

    private EduSemesterSaveReqVO request(String name) {
        EduSemesterSaveReqVO request = new EduSemesterSaveReqVO();
        request.setSemesterName(name);
        request.setSemesterStartDate(LocalDate.of(2026, 8, 1));
        request.setSemesterEndDate(LocalDate.of(2027, 1, 31));
        return request;
    }

    private EduSemesterUsageDO usage(String name, int count) {
        EduSemesterUsageDO usage = new EduSemesterUsageDO();
        usage.setSemesterName(name);
        usage.setLinkCount(count);
        usage.setActiveLinkCount(count);
        return usage;
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        DataSource dataSource() throws Exception {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:semester-cache-" + UUID.randomUUID()
                    + ";MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1");
            try (Connection connection = source.getConnection()) {
                connection.createStatement().execute("""
                        CREATE TABLE infra_config (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY, category VARCHAR(50), type TINYINT,
                            name VARCHAR(100), config_key VARCHAR(100), value VARCHAR(500), visible BOOLEAN,
                            remark VARCHAR(500), creator VARCHAR(64), updater VARCHAR(64),
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted BOOLEAN DEFAULT FALSE)
                        """);
            }
            return spy(source);
        }
        @Bean
        EduClassCourseMapper classCourseMapper() {
            return mock(EduClassCourseMapper.class);
        }
        @Bean
        ConfigMapper configMapper() {
            return mock(ConfigMapper.class);
        }
    }
}
