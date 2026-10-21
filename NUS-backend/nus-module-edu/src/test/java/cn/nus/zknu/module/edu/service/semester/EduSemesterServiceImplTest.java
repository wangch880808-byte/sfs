package cn.nus.zknu.module.edu.service.semester;

import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterSaveReqVO;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EduSemesterServiceImplTest {

    @Test
    void calculateAutomaticSemesterUsesJanuaryAsPreviousFirstTerm() {
        var period = EduSemesterServiceImpl.calculateAutomaticSemester(LocalDate.of(2027, 1, 15));

        assertEquals("2026-2027 上学期", period.semesterName());
        assertEquals(LocalDate.of(2026, 8, 1), period.startDate());
        assertEquals(LocalDate.of(2027, 1, 31), period.endDate());
    }

    @Test
    void calculateAutomaticSemesterSwitchesToSecondTermInFebruary() {
        var period = EduSemesterServiceImpl.calculateAutomaticSemester(LocalDate.of(2027, 2, 1));

        assertEquals("2026-2027 下学期", period.semesterName());
        assertEquals(LocalDate.of(2027, 2, 1), period.startDate());
        assertEquals(LocalDate.of(2027, 7, 31), period.endDate());
    }

    @Test
    void calculateAutomaticSemesterSwitchesAcademicYearInAugust() {
        var period = EduSemesterServiceImpl.calculateAutomaticSemester(LocalDate.of(2027, 8, 1));

        assertEquals("2027-2028 上学期", period.semesterName());
        assertEquals(LocalDate.of(2027, 8, 1), period.startDate());
        assertEquals(LocalDate.of(2028, 1, 31), period.endDate());
    }

    @Test
    void manualSaveDisablesAutomationInSameTransaction() throws Exception {
        DataSource dataSource = createDataSource();
        EduSemesterServiceImpl service = createService(dataSource);
        EduSemesterSaveReqVO reqVO = new EduSemesterSaveReqVO();
        reqVO.setSemesterName("2026-2027 上学期");
        reqVO.setSemesterStartDate(LocalDate.of(2026, 8, 20));
        reqVO.setSemesterEndDate(LocalDate.of(2027, 1, 15));

        service.saveCurrentSemester(reqVO);

        assertEquals("false", getConfigValue(dataSource, "edu.current.semester.auto-enabled"));
        assertEquals("2026-2027 上学期", getConfigValue(dataSource, "edu.current.semester.name"));
        assertEquals("2026-08-20", getConfigValue(dataSource, "edu.current.semester.start-date"));
        assertEquals("2027-01-15", getConfigValue(dataSource, "edu.current.semester.end-date"));
    }

    @Test
    void syncFailureDoesNotUndoSemesterSwitch() throws Exception {
        DataSource dataSource = createDataSource();
        EduSemesterServiceImpl service = createService(dataSource);
        EduPublicCourseSyncScheduler syncService = mock(EduPublicCourseSyncScheduler.class);
        doThrow(new IllegalStateException("同步失败")).when(syncService).requestAll();
        ReflectionTestUtils.setField(service, "publicCourseSyncScheduler", syncService);
        EduSemesterSaveReqVO request = new EduSemesterSaveReqVO();
        request.setSemesterName("2026-2027 上学期");
        request.setSemesterStartDate(LocalDate.of(2026, 8, 1));
        request.setSemesterEndDate(LocalDate.of(2027, 1, 31));

        service.saveCurrentSemester(request);

        assertEquals("2026-2027 上学期", getConfigValue(dataSource, "edu.current.semester.name"));
        verify(syncService).requestAll();
    }

    @Test
    void manualSaveIsCommittedBeforeQueueingAndDoesNotRunSyncOnCaller() throws Exception {
        DataSource dataSource = createDataSource();
        EduSemesterServiceImpl service = createService(dataSource);
        EduPublicCourseSyncService syncService = mock(EduPublicCourseSyncService.class);
        EduPublicCourseSyncScheduler scheduler = new EduPublicCourseSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "syncService", syncService);
        Queue<Runnable> tasks = new ArrayDeque<>();
        ReflectionTestUtils.setField(scheduler, "executor", (Executor) task -> {
            try {
                // 独立连接只能看到已经提交的配置。
                assertEquals("异步测试学期", getConfigValue(dataSource, "edu.current.semester.name"));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            tasks.add(task);
        });
        ReflectionTestUtils.setField(service, "publicCourseSyncScheduler", scheduler);
        EduSemesterSaveReqVO request = new EduSemesterSaveReqVO();
        request.setSemesterName("异步测试学期");
        request.setSemesterStartDate(LocalDate.of(2026, 8, 1));
        request.setSemesterEndDate(LocalDate.of(2027, 1, 31));

        service.saveCurrentSemester(request);

        assertEquals(1, tasks.size());
        verifyNoInteractions(syncService);
        tasks.remove().run();
        verify(syncService).syncAll();
    }

    @Test
    void automaticUpdatePreservesManualSettingWhenDisabled() throws Exception {
        DataSource dataSource = createDataSource();
        EduSemesterServiceImpl service = createService(dataSource);
        EduSemesterSaveReqVO reqVO = new EduSemesterSaveReqVO();
        reqVO.setSemesterName("手工学期");
        reqVO.setSemesterStartDate(LocalDate.of(2026, 9, 1));
        reqVO.setSemesterEndDate(LocalDate.of(2027, 1, 10));
        service.saveCurrentSemester(reqVO);

        String result = service.autoUpdateCurrentSemester();

        assertTrue(result.contains("自动更新已关闭"));
        assertEquals("手工学期", getConfigValue(dataSource, "edu.current.semester.name"));
    }

    @Test
    void enablingAutomationImmediatelyAppliesCurrentRule() throws Exception {
        DataSource dataSource = createDataSource();
        EduSemesterServiceImpl service = createService(dataSource);
        EduSemesterServiceImpl.EduSemesterPeriod expected =
                EduSemesterServiceImpl.calculateAutomaticSemester(LocalDate.now());

        service.setAutoEnabled(true);

        assertEquals("true", getConfigValue(dataSource, "edu.current.semester.auto-enabled"));
        assertEquals(expected.semesterName(), getConfigValue(dataSource, "edu.current.semester.name"));
        assertEquals(expected.startDate().toString(), getConfigValue(dataSource, "edu.current.semester.start-date"));
        assertEquals(expected.endDate().toString(), getConfigValue(dataSource, "edu.current.semester.end-date"));
    }

    private static EduSemesterServiceImpl createService(DataSource dataSource) {
        EduSemesterServiceImpl service = new EduSemesterServiceImpl();
        ReflectionTestUtils.setField(service, "dataSource", dataSource);
        ReflectionTestUtils.setField(service, "publicCourseSyncScheduler", mock(EduPublicCourseSyncScheduler.class));
        return service;
    }

    private static DataSource createDataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:semester-" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1");
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("""
                    CREATE TABLE infra_config (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        category VARCHAR(50) NOT NULL,
                        type TINYINT NOT NULL,
                        name VARCHAR(100) NOT NULL DEFAULT '',
                        config_key VARCHAR(100) NOT NULL DEFAULT '',
                        value VARCHAR(500) NOT NULL DEFAULT '',
                        visible BOOLEAN NOT NULL,
                        remark VARCHAR(500),
                        creator VARCHAR(64),
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updater VARCHAR(64),
                        update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
        }
        return dataSource;
    }

    private static String getConfigValue(DataSource dataSource, String key) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT value FROM infra_config WHERE config_key = ? AND deleted = 0")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }
}
