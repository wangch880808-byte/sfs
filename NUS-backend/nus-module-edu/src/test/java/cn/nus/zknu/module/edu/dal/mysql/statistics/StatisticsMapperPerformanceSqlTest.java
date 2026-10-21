package cn.nus.zknu.module.edu.dal.mysql.statistics;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 历史基线、修复前和当前 XML 均经过真实权限及分页插件，在同一份 H2 MySQL 数据上比较。 */
class StatisticsMapperPerformanceSqlTest extends BaseMockitoUnitTest {
    @Mock private PermissionCommonApi permissionApi;
    @Mock private DataPermissionRuleFactory ruleFactory;
    private SqlSessionFactory factory;
    private final List<String> executed = new ArrayList<>();
    private static final String PREFIX = StatisticsMapper.class.getName() + ".";

    @BeforeEach
    void setUp() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:stat_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        var rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("edu_class", "dept_id");
        rule.addDeptColumn("system_users", "dept_id");
        rule.addUserColumn("system_users", "id");
        rule.addDeptColumn("system_dept", "id");
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor());
        chain.addInnerInterceptor(new InnerInterceptor() {
            @Override
            public void beforePrepare(StatementHandler handler, Connection connection, Integer timeout) {
                var sql = PluginUtils.mpStatementHandler(handler).mPBoundSql();
                executed.add(sql.sql());
                // H2 不支持 MySQL 的动态 INTERVAL 语法；解析及权限改写完成后只适配日期运算。
                String monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
                sql.sql(sql.sql().replaceAll("(?i)DATE_SUB\\s*\\(CURDATE\\s*\\(\\),\\s*INTERVAL WEEKDAY\\s*\\(CURDATE\\s*\\(\\)\\) DAY\\)",
                        "DATE '" + monday + "'"));
            }
        });
        configuration.addInterceptor(chain);
        for (String resource : List.of("mapper/statistics/StatisticsMapper.xml", "statistics/StatisticsMapperLegacy.xml",
                "statistics/StatisticsMapperBeforeFix.xml")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession()) {
            execute(session, """
                CREATE TABLE edu_class (id BIGINT PRIMARY KEY, class_name VARCHAR(40), dept_id BIGINT, status INT, deleted INT);
                CREATE TABLE system_dept (id BIGINT PRIMARY KEY, parent_id BIGINT, deleted INT);
                CREATE TABLE capacity_course (id BIGINT PRIMARY KEY, course_name VARCHAR(40), teacher_Id BIGINT,
                    status INT, deleted INT, is_public_course INT, public_audit_status INT);
                CREATE TABLE edu_class_course (id BIGINT PRIMARY KEY, class_id BIGINT, course_id BIGINT,
                    semester_name VARCHAR(40), status INT, deleted INT, claim_teacher_id BIGINT);
                CREATE TABLE edu_homework (id BIGINT PRIMARY KEY, homework_name VARCHAR(40), class_id BIGINT, course_id BIGINT,
                    teacher_id BIGINT, semester_name VARCHAR(40), status INT, deleted INT, create_time TIMESTAMP, pass_score INT, score INT);
                CREATE TABLE system_users (id BIGINT PRIMARY KEY, nickname VARCHAR(40), dept_id BIGINT,
                    user_type INT, status INT, deleted INT);
                CREATE TABLE edu_homework_record (id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT,
                    status INT, score INT, deleted INT, create_time TIMESTAMP, submit_time TIMESTAMP);
                CREATE TABLE edu_homework_ai_review (id BIGINT PRIMARY KEY, homework_record_id BIGINT, status INT, deleted INT);
                CREATE TABLE capacity_course_member (id BIGINT PRIMARY KEY, course_id BIGINT, user_id BIGINT, deleted INT);
                CREATE TABLE edu_class_course_assistant (id BIGINT PRIMARY KEY, class_course_id BIGINT, teacher_id BIGINT, deleted INT);
                CREATE TABLE edu_message (id BIGINT PRIMARY KEY, class_id BIGINT, course_id BIGINT, type INT, read_status INT, deleted INT);
                INSERT INTO system_dept VALUES (10,100,0),(20,200,0),(100,0,0),(200,0,0);
                INSERT INTO edu_class VALUES (1,'本院',10,0,0),(2,'外院',20,0,0),(3,'归档',10,1,0),(4,'删除',10,0,1);
                INSERT INTO capacity_course VALUES
                    (1,'负责人课程',7,0,0,1,1),(2,'团队课程',8,0,0,1,1),(3,'助教课程',8,0,0,1,0),
                    (4,'归档课程',7,1,0,1,1),(5,'删除课程',7,0,1,1,1),(6,'无关课程',8,0,0,1,0);
                INSERT INTO edu_class_course (id,class_id,course_id,semester_name,status,deleted) VALUES
                    (1,1,1,' 2026-2027 秋 ',0,0),(2,1,1,'2026-2027 秋',0,0),
                    (3,2,1,'2026-2027 秋',0,0),(4,3,1,NULL,0,0),(5,4,1,NULL,0,0),
                    (6,1,2,NULL,0,0),(7,1,3,'   ',0,0),(8,1,4,NULL,0,0),
                    (9,1,5,NULL,0,0),(10,1,6,NULL,0,0),(11,1,6,NULL,1,0),(12,2,3,NULL,0,0),
                    (13,1,6,NULL,0,1);
                INSERT INTO capacity_course_member VALUES (1,2,7,0),(2,2,7,0),(3,6,7,1);
                INSERT INTO edu_class_course_assistant VALUES (1,7,7,0),(2,7,7,0),(3,10,7,1),(4,12,7,0);
                INSERT INTO edu_homework VALUES
                    (1,'正常',1,1,7,'2026-2027 秋',1,0,'2026-01-01',60,100),
                    (2,'任务空学期',1,1,7,NULL,2,0,'2026-01-02',60,100),
                    (3,'空白学期',1,1,8,'   ',1,0,'2026-01-03',60,100),
                    (4,'绑定空学期',1,2,7,'其他学期',1,0,'2026-01-04',60,100),
                    (5,'绑定空白',1,3,8,'其他学期',2,0,'2026-01-05',60,100),
                    (6,'不匹配',1,1,7,'旧学期',1,0,'2026-01-06',60,100),
                    (7,'任务归档',1,1,7,NULL,3,0,'2026-01-07',60,100),
                    (8,'任务草稿',1,1,7,NULL,0,0,'2026-01-08',60,100),
                    (9,'任务删除',1,1,7,NULL,1,1,'2026-01-09',60,100),
                    (10,'班归档',3,1,7,NULL,1,0,'2026-01-10',60,100),
                    (11,'班删除',4,1,7,NULL,1,0,'2026-01-11',60,100),
                    (12,'课归档',1,4,7,NULL,1,0,'2026-01-12',60,100),
                    (13,'课删除',1,5,7,NULL,1,0,'2026-01-13',60,100),
                    (14,'外院任务',2,1,7,'2026-2027 秋',1,0,'2026-01-14',60,100),
                    (15,'空绑定无关课',1,6,8,NULL,1,0,'2026-01-15',60,100);
                INSERT INTO system_users VALUES (100,'教师',10,1,0,0),(101,'停用',10,2,1,0);
                INSERT INTO edu_homework_ai_review VALUES (1,102,2,0),(2,102,4,0),(3,103,5,0),(4,202,1,0),
                    (5,203,2,1),(6,1402,4,0);
                INSERT INTO edu_message VALUES
                    (1,1,1,0,0,0),(2,1,2,0,0,0),(3,1,3,0,0,0),(4,2,3,0,0,0),
                    (5,3,1,0,0,0),(6,4,1,0,0,0),(7,1,4,0,0,0),(8,1,5,0,0,0),
                    (9,1,6,0,0,0),(10,1,1,1,0,0),(11,1,1,0,1,0),(12,1,1,0,0,1);
                """);
            for (int student = 1; student <= 12; student++) {
                execute(session, "INSERT INTO system_users VALUES (" + student + ",'学生" + student + "',"
                        + (student == 8 ? 20 : 10) + ",2,0," + (student == 9 ? 1 : 0) + ")");
            }
            for (int homework = 1; homework <= 15; homework++) {
                for (int student = 1; student <= 12; student++) {
                    int status = student <= 9 ? 2 : student == 10 ? 1 : student == 11 ? 3 : 4;
                    String score = student == 1 ? "NULL" : Integer.toString(student == 3 || student == 4 ? 30 : student * 10);
                    // 旧记录本周重交，首次创建时间仍在上周，必须不计入本周提交。
                    String created = student == 12 ? "DATEADD('DAY',-14,CURRENT_TIMESTAMP)" : "CURRENT_TIMESTAMP";
                    execute(session, "INSERT INTO edu_homework_record VALUES (" + (homework * 100 + student)
                            + "," + homework + "," + student + "," + status + "," + score + ","
                            + (student == 7 ? 1 : 0) + "," + created + ",CURRENT_TIMESTAMP)");
                }
            }
            session.commit();
        }
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allChangedQueriesMatchLegacyForGlobalAndCollegeAdministrators() throws Exception {
        for (boolean all : List.of(true, false)) {
            login(all);
            try (var session = factory.openSession()) {
                excludeArchivedHistoryForLegacyComparison(session);
                for (Long teacher : Arrays.asList(null, 7L, 8L)) {
                    var params = params(teacher);
                    for (String method : List.of("selectTotalStudents", "selectTotalTeachers", "selectTotalCourses",
                            "selectTotalHomeworks", "selectPendingPublicAuditCount", "selectWeekSubmissions",
                            "selectPendingCorrections", "selectAiCorrections", "selectTeacherCourseCount",
                            "selectUnreadMessages", "selectRecentHomeworks", "selectTeacherCourses", "selectCourseClasses",
                            "selectClassIdsByDept", "selectTotalPassRate", "selectWorkTrend", "selectCourseAnalysisAvg")) {
                        assertEquals(session.selectList("s.stat.legacy." + method, params),
                                session.selectList(PREFIX + method, params), method + " all=" + all + " teacher=" + teacher);
                    }
                }
                var mapper = session.getMapper(StatisticsMapper.class);
                assertEquals(all ? 7L : 6L, mapper.selectTotalHomeworks());
                assertEquals(63L, mapper.selectWeekSubmissions(null));
                assertEquals(14L, mapper.selectPendingCorrections(null));
                assertEquals(3L, mapper.selectAiCorrections());
                assertEquals(all ? 4L : 3L, mapper.selectUnreadMessages(7L));
                if (!all) {
                    assertTrue(executed.stream().anyMatch(sql -> sql.contains("visible_class.dept_id IN (10)")));
                }
            }
        }
    }

    @Test
    void scopedQueriesMatchLegacyAndAvoidUnrestrictedHomeworkSet() throws Exception {
        for (boolean all : List.of(true, false)) {
            login(all);
            try (var session = factory.openSession()) {
                excludeArchivedHistoryForLegacyComparison(session);
                // 单独覆盖每个可选范围，避免组合参数掩盖错误的分支条件。
                for (int scope = 0; scope < 7; scope++) {
                    var params = params(null);
                    params.put("classIds", null);
                    params.put("courseId", null);
                    params.put("academicYear", scope == 0 ? null : 2026);
                    switch (scope) {
                        case 2 -> params.put("teacherId", 7L);
                        case 3 -> params.put("courseId", 1L);
                        case 4 -> params.put("classIds", List.of(2L));
                        case 5 -> params.put("classIds", List.of());
                        case 6 -> {
                            params.put("teacherId", 8L);
                            params.put("courseId", 3L);
                            params.put("classIds", List.of(1L, 2L));
                        }
                        default -> { }
                    }
                    assertLegacyAndScope(session, "selectTotalPassRate", params,
                            scope == 2 || scope == 3 || scope == 4 || scope == 6, all);
                }
                for (long course = 1; course <= 6; course++) {
                    for (Long classId : Arrays.asList(null, 1L, 2L, 3L, 4L, 999L)) {
                        var params = params(null);
                        params.put("courseId", course);
                        params.put("classId", classId);
                        assertLegacyAndScope(session, "selectWorkTrend", params, true, all);
                        assertLegacyAndScope(session, "selectCourseAnalysisAvg", params, true, all);
                    }
                }
                for (long id = 1; id <= 16; id++) {
                    var params = Map.<String, Object>of("homeworkId", id);
                    assertLegacyAndScope(session, "selectHomeworkTopStudents", params, true, all);
                    assertLegacyAndScope(session, "selectHomeworkLastStudents", params, true, all);
                }
                for (Long teacher : Arrays.asList(null, 7L, 8L, 999L)) {
                    var params = params(teacher);
                    assertLegacyAndScope(session, "selectWeekSubmissions", params, teacher != null, all);
                    assertLegacyAndScope(session, "selectPendingCorrections", params, teacher != null, all);
                    assertLegacyAndScope(session, "selectRecentHomeworks", params, true, all);
                }
            }
        }
    }

    @Test
    void mapperParameterMapsSupportScopedAndGlobalBranches() {
        login(false);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(StatisticsMapper.class);
            executed.clear();
            mapper.selectWeekSubmissions(7L);
            assertScope(true);
            executed.clear();
            mapper.selectWeekSubmissions(null);
            assertScope(false);
            executed.clear();
            mapper.selectPendingCorrections(7L);
            assertScope(true);
            executed.clear();
            mapper.selectPendingCorrections(null);
            assertScope(false);
            executed.clear();
            mapper.selectTotalPassRate(null, null, null, null, 0.8, 0.9);
            assertScope(false);
            executed.clear();
            mapper.selectTotalPassRate(null, List.of(2L), null, null, 0.8, 0.9);
            assertScope(true);
            executed.clear();
            mapper.selectWorkTrend(1L, null);
            assertScope(true);
            executed.clear();
            mapper.selectCourseAnalysisAvg(1L, 2L);
            assertScope(true);
            executed.clear();
            mapper.selectRecentHomeworks(7L);
            assertScope(true);
        }
    }

    @Test
    void windowsMatchLegacyScoresAndStudentsExceptUnspecifiedTieOrder() throws Exception {
        for (boolean all : List.of(true, false)) {
            login(all);
            try (var session = factory.openSession()) {
                excludeArchivedHistoryForLegacyComparison(session);
                var mapper = session.getMapper(StatisticsMapper.class);
                var ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
                executed.clear();
                var beforeFix = session.<Map<String, Object>>selectList("s.stat.beforeFix.selectHomeworkStudentExtremes",
                        Map.of("homeworkIds", ids));
                boolean classPermissionBeforeFix = hasClassPermission();
                executed.clear();
                var rows = mapper.selectHomeworkStudentExtremes(ids);
                assertScope(true);
                assertEquals(classPermissionBeforeFix, hasClassPermission());
                assertEquals(beforeFix.size(), rows.size());
                assertEquals(studentsWithoutRanks(beforeFix), studentsWithoutRanks(rows));
                for (Long id : ids) {
                    var current = rows.stream().filter(row -> ((Number) row.get("homeworkId")).longValue() == id).toList();
                    for (boolean top : List.of(true, false)) {
                        String rank = top ? "topRank" : "lastRank";
                        var ranked = current.stream().filter(row -> ((Number) row.get(rank)).intValue() <= 5)
                                .sorted(Comparator.comparingInt(row -> ((Number) row.get(rank)).intValue())).toList();
                        var old = session.<Map<String, Object>>selectList("s.stat.legacy."
                                + (top ? "selectHomeworkTopStudents" : "selectHomeworkLastStudents"), Map.of("homeworkId", id));
                        assertEquals(old.stream().map(row -> row.get("score")).toList(),
                                ranked.stream().map(row -> row.get("score")).toList(), "任务" + id + " " + rank);
                        assertEquals(old.stream().map(row -> row.get("studentId")).collect(java.util.stream.Collectors.toSet()),
                                ranked.stream().map(row -> row.get("studentId")).collect(java.util.stream.Collectors.toSet()));
                    }
                }
                assertTrue(mapper.selectHomeworkStudentExtremes(List.of()).isEmpty());
                assertTrue(mapper.selectHomeworkStudentExtremes(null).isEmpty());
                assertTrue(executed.stream().anyMatch(sql -> sql.contains("ROW_NUMBER() OVER")));
                if (!all) {
                    assertTrue(executed.stream().anyMatch(sql -> sql.contains("su.dept_id IN (10)")));
                }
            }
        }
    }

    @Test
    void boundaryTiesAndNullScoresStillReturnExactlyFiveWithoutExpandingTies() throws Exception {
        login(true);
        try (var session = factory.openSession()) {
            execute(session, "UPDATE edu_homework_record SET score = 50 WHERE homework_id = 1 AND student_id = 6");
            execute(session, "UPDATE edu_homework_record SET score = NULL WHERE homework_id = 1 AND student_id = 2");
            var rows = session.getMapper(StatisticsMapper.class).selectHomeworkStudentExtremes(List.of(1L));
            for (boolean top : List.of(true, false)) {
                String rank = top ? "topRank" : "lastRank";
                var ranked = rows.stream().filter(row -> ((Number) row.get(rank)).intValue() <= 5)
                        .sorted(Comparator.comparingInt(row -> ((Number) row.get(rank)).intValue())).toList();
                var old = session.<Map<String, Object>>selectList("s.stat.legacy."
                        + (top ? "selectHomeworkTopStudents" : "selectHomeworkLastStudents"), Map.of("homeworkId", 1L));
                assertEquals(5, ranked.size());
                assertEquals(old.stream().map(row -> row.get("score")).toList(),
                        ranked.stream().map(row -> row.get("score")).toList());
                for (var row : ranked) {
                    long student = ((Number) row.get("studentId")).longValue();
                    assertTrue(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 8L).contains(student));
                }
            }
        }
    }

    @Test
    void publicClaimsScopeOptionsMetricsAndMessageRecipientsThroughRealPlugins() throws Exception {
        for (boolean all : List.of(true, false)) {
            login(all);
            try (var session = factory.openSession()) {
                execute(session, """
                    INSERT INTO capacity_course VALUES
                        (20,'公共认领',8,0,0,0,2),(21,'专属团队',8,0,0,1,0),
                        (22,'公共团队',8,0,0,0,2),(23,'公共负责',7,0,0,0,2),
                        (24,'公共归档认领',8,0,0,0,2),(25,'公共删除认领',8,0,0,0,2);
                    INSERT INTO edu_class_course VALUES
                        (20,1,20,' 2026-2027 秋 ',0,0,7),(21,2,20,'2026-2027 秋',0,0,9),
                        (22,1,20,'旧学期',0,0,9),(23,1,21,NULL,0,0,NULL),
                        (24,1,22,NULL,0,0,NULL),(25,1,23,NULL,0,0,NULL),
                        (26,1,24,NULL,1,0,7),(27,1,25,NULL,0,1,7);
                    INSERT INTO capacity_course_member VALUES
                        (20,20,7,0),(21,21,7,0),(22,22,7,0),(23,22,7,0);
                    INSERT INTO edu_class_course_assistant VALUES (20,21,7,0),(21,21,7,0);
                    INSERT INTO edu_homework VALUES
                        (20,'认领班他人任务',1,20,9,'2026-2027 秋',1,0,'2026-02-01',60,100),
                        (21,'他人班自己任务',2,20,7,'2026-2027 秋',1,0,'2026-02-02',60,100),
                        (22,'旧学期任务',1,20,7,'旧学期',1,0,'2026-02-03',60,100),
                        (23,'空学期任务',1,20,9,'  ',1,0,'2026-02-04',60,100),
                        (24,'归档认领任务',1,24,7,NULL,1,0,'2026-02-05',60,100),
                        (25,'删除认领任务',1,25,7,NULL,1,0,'2026-02-06',60,100),
                        (26,'认领班本人任务',1,20,7,'2026-2027 秋',1,0,'2026-02-07',60,100);
                    INSERT INTO edu_homework_record VALUES
                        (2001,20,1,2,50,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                        (2101,21,1,2,70,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                        (2201,22,1,2,80,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                        (2301,23,1,2,95,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                        (2401,24,1,2,60,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                        (2501,25,1,2,60,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                        (2601,26,1,2,90,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
                    INSERT INTO edu_message VALUES
                        (20,1,20,0,0,0),(21,2,20,0,0,0),(22,1,22,0,0,0),(23,1,23,0,0,0),
                        (24,1,24,0,0,0),(25,1,25,0,0,0);
                    """);
                var mapper = session.getMapper(StatisticsMapper.class);
                assertEquals(Set.of(1L,2L,4L,20L,21L,23L), ids(mapper.selectTeacherCourses(7L), "id"));
                assertEquals(Set.of(1L), ids(mapper.selectCourseClasses(7L,20L), "id"));
                assertEquals(all ? Set.of(1L,2L) : Set.of(1L), ids(mapper.selectCourseClasses(8L,20L), "id"));
                assertEquals(Set.of(1L), ids(mapper.selectCourseClasses(7L,21L), "id"));
                assertTrue(mapper.selectCourseClasses(7L,22L).isEmpty());
                assertEquals(3L, mapper.selectTeacherCourseCount(7L));
                assertEquals(Set.of(26L), ids(mapper.selectWorkTrend(20L,null,7L), "homeworkId"));
                assertEquals(Set.of(26L), ids(mapper.selectCourseAnalysisAvg(20L,null,7L), "homeworkId"));
                assertTrue(mapper.selectWorkTrend(20L,2L,7L).isEmpty());
                assertEquals(all ? Set.of(20L,21L,22L,23L,26L) : Set.of(20L,22L,23L,26L),
                        ids(mapper.selectWorkTrend(20L,null,null), "homeworkId"));
                executed.clear();
                assertEquals(1, ((Number)mapper.selectTotalPassRate(7L,null,20L,2026,0.8,0.9)
                        .get("totalRecords")).intValue());
                assertEquals(1, executed.size());
                assertTrue(executed.get(0).contains("h.teacher_id = ?"));
                assertTrue(executed.get(0).contains("UNION ALL"));
                // 总合格率的班级可见子查询位于 ON，沿用已有拦截器遍历口径。
                assertEquals(5, ((Number)mapper.selectTotalPassRate(8L,null,20L,null,0.8,0.9)
                        .get("totalRecords")).intValue());
                assertTrue(mapper.selectStatisticsClassAccess(7L,1L));
                assertFalse(mapper.selectStatisticsClassAccess(99L,1L));
                assertEquals(all ? 7L : 5L, mapper.selectUnreadMessages(7L));
                assertEquals(all ? 5L : 4L, mapper.selectUnreadMessages(8L));
                assertEquals(0L, mapper.selectUnreadMessages(99L));
                assertTrue(executed.stream().anyMatch(sql -> sql.contains("claim_binding.claim_teacher_id")));
                if (!all) assertTrue(executed.stream().anyMatch(sql -> sql.contains("ec.dept_id IN (10)")));
                session.rollback(true);
            }
        }
    }

    @Test
    void statisticsClassAccessUsesActualClaimAndCollegeScopeInsteadOfPublicTeam() throws Exception {
        for (boolean all : List.of(true, false)) {
            login(all);
            try (var session = factory.openSession()) {
                execute(session, """
                    INSERT INTO edu_class VALUES (5,'本人认领',10,0,0),(6,'只有团队',10,0,0),
                        (7,'别人认领',10,0,0),(8,'外院认领',20,0,0);
                    INSERT INTO capacity_course VALUES (30,'认领课',8,0,0,0,2),(31,'团队课',8,0,0,0,2),
                        (32,'他人课',8,0,0,0,2),(33,'外院课',8,0,0,0,2);
                    INSERT INTO edu_class_course VALUES (30,5,30,NULL,0,0,7),(31,6,31,NULL,0,0,NULL),
                        (32,7,32,NULL,0,0,9),(33,8,33,NULL,0,0,7);
                    INSERT INTO capacity_course_member VALUES (30,31,7,0);
                    """);
                var mapper = session.getMapper(StatisticsMapper.class);
                assertTrue(mapper.selectStatisticsClassAccess(7L,5L));
                assertFalse(mapper.selectStatisticsClassAccess(7L,6L));
                assertFalse(mapper.selectStatisticsClassAccess(7L,7L));
                assertEquals(all, mapper.selectStatisticsClassAccess(7L,8L));
                session.rollback(true);
            }
        }
    }

    @Test
    void exclusiveAssistantCourseAccessAndClaimedCourseCountRespectClassStateAndScope() throws Exception {
        for (boolean all : List.of(true, false)) {
            login(all);
            try (var session = factory.openSession()) {
                execute(session, """
                    INSERT INTO capacity_course VALUES (30,'专属助教',8,0,0,1,0),(31,'公共助教',8,0,0,0,2),
                        (32,'归档班认领',8,0,0,0,2),(33,'删除班认领',8,0,0,0,2),
                        (34,'外院认领',8,0,0,0,2),(35,'归档班课',8,0,0,0,2),
                        (36,'正常认领',8,0,0,0,2);
                    INSERT INTO edu_class_course VALUES (30,1,30,NULL,0,0,NULL),(31,1,31,NULL,0,0,NULL),
                        (32,3,32,NULL,0,0,7),(33,4,33,NULL,0,0,7),
                        (34,2,34,NULL,0,0,7),(35,1,35,NULL,1,0,7),(36,1,36,NULL,0,0,7);
                    INSERT INTO edu_class_course_assistant VALUES (30,30,7,0),(31,31,7,0);
                    """);
                var mapper = session.getMapper(StatisticsMapper.class);
                assertTrue(mapper.selectStatisticsCourseAccess(7L,30L));
                assertFalse(mapper.selectStatisticsCourseAccess(9L,30L));
                assertFalse(mapper.selectStatisticsCourseAccess(7L,31L));
                assertEquals(all ? 3L : 2L, mapper.selectTeacherCourseCount(7L));
                session.rollback(true);
            }
        }
    }

    @Test
    void unreadCountsStartFromDistinctTeacherBindingsAndDoNotMultiplyAcrossSemesters() throws Exception {
        login(true);
        try (var session = factory.openSession()) {
            execute(session, "INSERT INTO edu_class_course VALUES (40,1,1,'其他学期',0,0,NULL)");
            executed.clear();
            assertEquals(4L, session.getMapper(StatisticsMapper.class).selectUnreadMessages(7L));
            assertEquals(1, executed.size());
            assertTrue(executed.get(0).contains("SELECT DISTINCT cc.class_id, cc.course_id"));
            assertTrue(executed.get(0).contains("m.class_id = scope.class_id"));
        }
    }

    // 历史基线仍用于验证在用数据的结果、排序及执行结构；封存的新口径由独立场景验证。
    private void excludeArchivedHistoryForLegacyComparison(SqlSession session) throws Exception {
        execute(session, "DELETE FROM edu_homework WHERE status = 3 OR class_id = 3 OR course_id = 4");
        execute(session, "DELETE FROM edu_class WHERE status = 1");
        execute(session, "DELETE FROM capacity_course WHERE status = 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"selectTotalPassRate", "selectWorkTrend", "selectCourseAnalysisAvg",
            "selectHomeworkTopStudents", "selectHomeworkLastStudents", "selectHomeworkStudentExtremes"})
    void analysisIncludesArchivedHistoryButExcludesDraftsAndEveryDeletedRelation(String method) throws Exception {
        login(false);
        try (var session = factory.openSession()) {
            createAnalysisFixture(session);
            var params = params(7L);
            params.put("classIds", List.of(1L));
            params.put("homeworkId", 1L);
            params.put("homeworkIds", List.of(1L));
            params.put("classId", 1L);
            for (String archivedTable : List.of("edu_homework", "edu_class", "capacity_course", "edu_class_course")) {
                execute(session, "UPDATE " + archivedTable + " SET status = " + (archivedTable.equals("edu_homework") ? 3 : 1));
                assertAnalysisRows(session, method, params, true);
                execute(session, "UPDATE " + archivedTable + " SET status = " + (archivedTable.equals("edu_homework") ? 1 : 0));
            }
            execute(session, "UPDATE edu_homework SET status = 3");
            execute(session, "UPDATE edu_class SET status = 1");
            execute(session, "UPDATE capacity_course SET status = 1");
            execute(session, "UPDATE edu_class_course SET status = 1");
            // 全量、指定课程、指定班级和教师 UNION 两支都要保留封存数据。
            if (method.equals("selectTotalPassRate")) {
                for (Long teacher : Arrays.asList(null, 7L)) {
                    params.put("teacherId", teacher);
                    params.put("courseId", null);
                    params.put("classIds", null);
                    assertAnalysisRows(session, method, params, true);
                    params.put("courseId", 1L);
                    assertAnalysisRows(session, method, params, true);
                    params.put("courseId", null);
                    params.put("classIds", List.of(1L));
                    assertAnalysisRows(session, method, params, true);
                }
                execute(session, "UPDATE capacity_course SET is_public_course = 0, teacher_Id = 8");
                params.put("teacherId", 8L);
                assertAnalysisRows(session, method, params, true);
                execute(session, "UPDATE capacity_course SET is_public_course = 1, teacher_Id = 7");
                params.put("teacherId", 7L);
                params.put("courseId", 1L);
            }
            assertAnalysisRows(session, method, params, true);
            for (String deletedTable : List.of("edu_homework", "edu_class", "capacity_course", "edu_class_course", "edu_homework_record")) {
                execute(session, "UPDATE " + deletedTable + " SET deleted = 1");
                assertAnalysisRows(session, method, params, false);
                execute(session, "UPDATE " + deletedTable + " SET deleted = 0");
            }
            execute(session, "UPDATE edu_homework SET status = 0");
            assertAnalysisRows(session, method, params, false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"selectTeacherCourses", "selectCourseClasses", "selectClassIdsByDept", "selectAnalysisClassCourseExists"})
    void analysisSelectorsAndAiScopeIncludeArchivedButExcludeDeletedEntities(String method) throws Exception {
        login(false);
        try (var session = factory.openSession()) {
            createAnalysisFixture(session);
            execute(session, "UPDATE edu_homework SET status = 3");
            execute(session, "UPDATE edu_class SET status = 1");
            execute(session, "UPDATE capacity_course SET status = 1");
            execute(session, "UPDATE edu_class_course SET status = 1");
            var params = params(7L);
            params.put("classId", 1L);
            params.put("majorId", 10L);
            assertSelectorRows(session, method, params, true);
            List<String> deletedTables = switch (method) {
                case "selectTeacherCourses" -> List.of("capacity_course");
                case "selectClassIdsByDept" -> List.of("edu_class", "system_dept");
                default -> List.of("edu_class", "capacity_course", "edu_class_course");
            };
            for (String table : deletedTables) {
                execute(session, "UPDATE " + table + " SET deleted = 1");
                assertSelectorRows(session, method, params, false);
                execute(session, "UPDATE " + table + " SET deleted = 0");
            }
            execute(session, "UPDATE edu_class SET dept_id = 20");
            if (!method.equals("selectTeacherCourses")) assertSelectorRows(session, method, params, false);
        }
    }

    @Test
    void dashboardsStillExcludeArchivedHistoryAndDrafts() throws Exception {
        login(true);
        try (var session = factory.openSession()) {
            createAnalysisFixture(session);
            execute(session, "INSERT INTO edu_homework_ai_review VALUES (1,1,2,0)");
            var mapper = session.getMapper(StatisticsMapper.class);
            for (String table : List.of("edu_homework", "edu_class", "capacity_course", "edu_class_course")) {
                execute(session, "UPDATE " + table + " SET status = " + (table.equals("edu_homework") ? 3 : 1));
                assertEquals(0L, mapper.selectTotalHomeworks());
                assertEquals(0L, mapper.selectWeekSubmissions(null));
                assertEquals(0L, mapper.selectWeekSubmissions(7L));
                assertEquals(0L, mapper.selectAiCorrections());
                assertTrue(mapper.selectRecentHomeworks(7L).isEmpty());
                execute(session, "UPDATE edu_homework_record SET status = 1");
                session.clearCache();
                assertEquals(0L, mapper.selectPendingCorrections(null));
                assertEquals(0L, mapper.selectPendingCorrections(7L));
                execute(session, "UPDATE edu_homework_record SET status = 2");
                execute(session, "UPDATE " + table + " SET status = " + (table.equals("edu_homework") ? 1 : 0));
            }
            execute(session, "UPDATE edu_homework SET status = 0");
            assertEquals(0L, mapper.selectTotalHomeworks());
            assertEquals(0L, mapper.selectWeekSubmissions(null));
            assertEquals(0L, mapper.selectAiCorrections());
            assertTrue(mapper.selectRecentHomeworks(7L).isEmpty());
            execute(session, "UPDATE capacity_course SET status = 1");
            assertEquals(0L, mapper.selectTotalCourses());
            assertEquals(0L, mapper.selectTeacherCourseCount(7L));
        }
    }

    @Test
    void archivedPublicHomeworkStillRequiresCurrentClaimTeacherAndSemester() throws Exception {
        login(false);
        try (var session = factory.openSession()) {
            createAnalysisFixture(session);
            execute(session, "UPDATE capacity_course SET is_public_course = 0, teacher_Id = 8, status = 1");
            execute(session, "UPDATE edu_class SET status = 1");
            execute(session, "UPDATE edu_homework SET status = 3, semester_name = '2026-2027 秋'");
            var mapper = session.getMapper(StatisticsMapper.class);
            assertEquals(Set.of(1L), ids(mapper.selectWorkTrend(1L,1L,7L), "homeworkId"));
            assertTrue(mapper.selectWorkTrend(1L,1L,9L).isEmpty());
            assertEquals(1, ((Number)mapper.selectTotalPassRate(7L,null,1L,2026,0.8,0.9).get("totalRecords")).intValue());
            execute(session, "UPDATE edu_class_course SET claim_teacher_id = NULL");
            assertTrue(mapper.selectWorkTrend(1L,1L,7L).isEmpty());
            assertTrue(mapper.selectCourseAnalysisAvg(1L,1L,7L).isEmpty());
            assertEquals(0, ((Number)mapper.selectTotalPassRate(7L,null,1L,2026,0.8,0.9).get("totalRecords")).intValue());
            assertEquals(Set.of(1L), ids(mapper.selectWorkTrend(1L,1L,null), "homeworkId"));
            execute(session, "UPDATE edu_class_course SET claim_teacher_id = 7, semester_name = '旧学期'");
            assertTrue(mapper.selectWorkTrend(1L,1L,7L).isEmpty());
            execute(session, "UPDATE edu_class_course SET semester_name = '2026-2027 秋', status = 1");
            assertTrue(mapper.selectWorkTrend(1L,1L,7L).isEmpty());
            assertEquals(Set.of(1L), ids(mapper.selectWorkTrend(1L,1L,null), "homeworkId"));
        }
    }

    @Test
    void expandedStatusesKeepTeacherIndexAvailableForActualPassRateSql() throws Exception {
        login(false);
        try (var session = factory.openSession()) {
            createAnalysisFixture(session);
            execute(session, "CREATE INDEX idx_edu_homework_teacher_status ON edu_homework(teacher_id,status,id)");
            execute(session, "INSERT INTO edu_homework (id,teacher_id,status,deleted,class_id,course_id) "
                    + "SELECT 100 + x,100 + x,1,0,1,1 FROM SYSTEM_RANGE(1,2000) sample(x)");
            execute(session, "UPDATE edu_homework SET status = 3");
            var params = params(7L);
            executed.clear();
            session.selectList(PREFIX + "selectTotalPassRate", params);
            var statement = factory.getConfiguration().getMappedStatement(PREFIX + "selectTotalPassRate");
            var boundSql = statement.getBoundSql(params);
            // 复核实际权限改写后的 SQL，H2 执行计划只证明索引可用，MySQL 的选择仍需真实数据确认。
            try (var explain = session.getConnection().prepareStatement("EXPLAIN " + executed.get(0))) {
                new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, params, boundSql)
                        .setParameters(explain);
                try (var result = explain.executeQuery()) {
                    assertTrue(result.next());
                    String plan = result.getString(1).toLowerCase(Locale.ROOT);
                    assertTrue(plan.contains("primary_key"), plan);
                    assertFalse(plan.contains("edu_homework.tablescan"), plan);
                }
            }
            // 聚合查询可由记录按任务主键回查；教师分支本身仍可用 V20 的教师及状态索引。
            try (var explain = session.getConnection().createStatement();
                 var result = explain.executeQuery("EXPLAIN SELECT h.id FROM edu_homework h "
                         + "WHERE h.teacher_id = 7 AND h.deleted = 0 AND h.status IN (1,2,3)")) {
                assertTrue(result.next());
                assertTrue(result.getString(1).toLowerCase(Locale.ROOT).contains("idx_edu_homework_teacher_status"),
                        result.getString(1));
            }
        }
    }

    private void createAnalysisFixture(SqlSession session) throws Exception {
        for (String table : List.of("edu_homework_record", "edu_homework", "edu_class_course", "edu_class",
                "capacity_course", "system_users", "edu_homework_ai_review", "capacity_course_member")) {
            execute(session, "DELETE FROM " + table);
        }
        execute(session, """
            INSERT INTO edu_class VALUES (1,'分析班',10,0,0);
            INSERT INTO capacity_course VALUES (1,'分析课',7,0,0,1,0);
            INSERT INTO edu_class_course VALUES (1,1,1,'2026-2027 秋',0,0,7);
            INSERT INTO edu_homework VALUES (1,'分析任务',1,1,7,'  ',1,0,'2026-01-01',60,100);
            INSERT INTO system_users VALUES (1,'学生',10,2,0,0);
            INSERT INTO edu_homework_record VALUES (1,1,1,2,80,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
            """);
    }

    private void assertAnalysisRows(SqlSession session, String method, Map<String, Object> params, boolean included) {
        session.clearCache();
        executed.clear();
        var rows = session.<Map<String,Object>>selectList(PREFIX + method, params);
        assertEquals(1, executed.size(), method);
        if (method.equals("selectTotalPassRate")) {
            assertEquals(included ? 1 : 0, ((Number)rows.get(0).get("totalRecords")).intValue(), method);
        } else {
            assertEquals(included ? 1 : 0, rows.size(), method);
            if (included) assertEquals(80, ((Number)rows.get(0).get(method.equals("selectWorkTrend")
                    || method.equals("selectCourseAnalysisAvg") ? "avgScore" : "score")).intValue());
        }
        assertTrue(executed.get(0).contains("h.status IN (1, 2, 3)"), method);
    }

    private void assertSelectorRows(SqlSession session, String method, Map<String,Object> params, boolean included) {
        session.clearCache();
        executed.clear();
        var rows = session.selectList(PREFIX + method, params);
        assertEquals(1, executed.size(), method);
        if (method.equals("selectAnalysisClassCourseExists")) assertEquals(included, rows.get(0));
        else assertEquals(included ? 1 : 0, rows.size(), method);
        if (!method.equals("selectTeacherCourses")) assertTrue(executed.get(0).contains("IN (10)"), method);
    }

    private Set<Long> ids(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(row -> ((Number)row.get(key)).longValue())
                .collect(java.util.stream.Collectors.toSet());
    }

    private void assertLegacyAndScope(SqlSession session, String method, Map<String, Object> params,
                                      boolean restricted, boolean all) {
        session.clearCache();
        var old = session.selectList("s.stat.legacy." + method, params);
        executed.clear();
        var beforeFix = session.selectList("s.stat.beforeFix." + method, params);
        boolean classPermissionBeforeFix = hasClassPermission();
        executed.clear();
        assertEquivalentRows(method, old, session.selectList(PREFIX + method, params),
                method + " all=" + all + " params=" + params);
        assertEquivalentRows(method, beforeFix, old, method + " 修复前基线 all=" + all + " params=" + params);
        assertScope(restricted);
        // ON 内子查询的权限遍历是已有行为，不能在性能修复中悄然改变统计范围。
        assertEquals(classPermissionBeforeFix, hasClassPermission(), method + " 班级权限 all=" + all);
        if (Set.of("selectWorkTrend", "selectCourseAnalysisAvg", "selectRecentHomeworks").contains(method)) {
            assertEquals(!all, hasClassPermission(), method + " WHERE 子查询班级权限");
        }
    }

    private void assertEquivalentRows(String method, List<?> expected, List<?> actual, String context) {
        if (Set.of("selectHomeworkTopStudents", "selectHomeworkLastStudents").contains(method)) {
            // 原 SQL 只按分数排序，同分学生的相对次序不是接口承诺。
            assertEquals(expected.size(), actual.size(), context);
            assertEquals(new HashSet<>(expected), new HashSet<>(actual), context);
            assertEquals(expected.stream().map(row -> ((Map<?, ?>) row).get("score")).toList(),
                    actual.stream().map(row -> ((Map<?, ?>) row).get("score")).toList(), context);
        } else {
            assertEquals(expected, actual, context);
        }
    }

    private Set<Map<String, Object>> studentsWithoutRanks(List<Map<String, Object>> rows) {
        // 同分记录的编号没有稳定顺序，只比较实际进入名单的学生及其分数。
        return rows.stream().map(row -> {
            Map<String, Object> student = new HashMap<>(row);
            student.remove("topRank");
            student.remove("lastRank");
            return student;
        }).collect(java.util.stream.Collectors.toSet());
    }

    private boolean hasClassPermission() {
        return executed.get(0).contains("visible_class.dept_id IN (10)");
    }

    private void assertScope(boolean restricted) {
        assertEquals(1, executed.size());
        String sql = executed.get(0).replaceAll("\\s+", " ");
        if (restricted) {
            assertTrue(sql.contains("EXISTS (SELECT 1 FROM edu_class visible_class"), sql);
            assertTrue(sql.contains("visible_class.id = h.class_id"), sql);
            assertFalse(sql.contains("visible_homework"), sql);
        } else {
            assertTrue(sql.contains("h.id IN (SELECT DISTINCT visible_homework.id"), sql);
        }
    }

    private Map<String, Object> params(Long teacher) {
        Map<String, Object> params = new HashMap<>();
        params.put("teacherId", teacher);
        params.put("classIds", List.of(1L, 2L));
        params.put("courseId", 1L);
        params.put("classId", null);
        params.put("collegeId", null);
        params.put("majorId", null);
        params.put("academicYear", 2026);
        params.put("goodRatio", 0.8);
        params.put("excellentRatio", 0.9);
        return params;
    }

    private void login(boolean all) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(50L).setUserType(2), new MockHttpServletRequest());
        var permission = new DeptDataPermissionRespDTO();
        permission.setAll(all);
        permission.setSelf(false);
        permission.setDeptIds(Set.of(10L));
        lenient().when(permissionApi.getDeptDataPermission(50L)).thenReturn(permission);
    }

    private void execute(SqlSession session, String sql) throws Exception {
        try (Statement statement = session.getConnection().createStatement()) {
            statement.execute(sql);
        }
        session.clearCache();
    }
}
