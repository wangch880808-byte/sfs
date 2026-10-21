package cn.nus.zknu.module.edu.dal.mysql.statistics;

import cn.nus.zknu.module.edu.dal.mysql.student.StudentCapacityMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证实际 Mapper SQL 的任务统计与审批计数口径。 */
class EduStatisticsMetricsMapperTest {

    @Test
    void pendingAuditIncludesArchivedCoursesButExcludesOtherStatesAndDeletedRows() throws Exception {
        try (SqlSession session = openSession("statistics/StatisticsMapper.xml")) {
            execute(session, """
                    CREATE TABLE capacity_course (
                        id BIGINT PRIMARY KEY, is_public_course INT, public_audit_status INT,
                        status INT, deleted INT);
                    INSERT INTO capacity_course VALUES
                        (1, 0, 1, 0, 0), (2, 0, 1, 1, 0),
                        (3, 0, 2, 0, 0), (4, 0, 0, 0, 0), (5, 0, 3, 0, 0),
                        (6, 1, 1, 0, 0), (7, 0, 1, 0, 1);
                    """);

            assertEquals(2, session.getMapper(StatisticsMapper.class).selectPendingPublicAuditCount());
        }
    }

    @Test
    void overallCountsIncludeUnindicatedTasksAndPreserveAssignmentAndHistoryScope() throws Exception {
        try (SqlSession session = openSession("student/StudentCapacityMapper.xml")) {
            createStudentTables(session);
            execute(session, """
                    INSERT INTO capacity_indicator VALUES (1, '教学能力', 1, 0);
                    INSERT INTO edu_homework VALUES
                        (1, 9, 1, 60, 1, 0), (2, 9, NULL, 60, 1, 0),
                        (3, 9, NULL, 60, 1, 0), (4, 9, NULL, 60, 1, 0),
                        (5, 9, NULL, 60, 3, 0), (6, 9, NULL, 60, 3, 0),
                        (7, 9, NULL, 60, 0, 0), (8, 9, NULL, 60, 1, 1),
                        (9, 9, NULL, 60, 1, 0), (10, 9, NULL, 60, 1, 0),
                        (11, 10, NULL, 60, 1, 0), (12, 9, NULL, 60, 1, 0);
                    INSERT INTO edu_homework_assignee VALUES
                        (1, 7, 0), (2, 7, 0), (3, 7, 0), (4, 7, 0), (5, 7, 0),
                        (6, 7, 0), (7, 7, 0), (8, 7, 0), (9, 8, 0), (10, 7, 1),
                        (11, 7, 0), (12, 7, 0);
                    INSERT INTO edu_homework_record VALUES
                        (1, 1, 7, 2, 80, 0), (2, 2, 7, 2, 90, 0),
                        (3, 3, 7, 4, 40, 0), (4, 4, 7, 2, NULL, 0),
                        (5, 5, 7, 2, 50, 0), (7, 7, 7, 2, 90, 0),
                        (8, 8, 7, 2, 90, 0), (9, 9, 7, 2, 90, 0),
                        (10, 10, 7, 2, 90, 0), (11, 11, 7, 2, 90, 0),
                        (12, 12, 7, 2, 90, 1);
                    """);

            StudentCapacityMapper mapper = session.getMapper(StudentCapacityMapper.class);
            Map<String, Object> stats = mapper.selectOverallStats(7L, List.of(9L));
            assertCount(stats, "totalTasks", 6);
            assertCount(stats, "submittedTasks", 5);
            assertCount(stats, "pendingReviewTasks", 1);
            assertCount(stats, "evaluatedTasks", 3);
            assertCount(stats, "qualifiedTasks", 2);
            assertCount(stats, "unindicatedEvaluatedCount", 2);
            List<Map<String, Object>> rates = mapper.selectFirstIndicatorRates(7L, List.of(9L));
            assertEquals(1, rates.size());
            assertCount(rates.get(0), "totalTasks", 1);
            assertCount(rates.get(0), "evaluatedTasks", 1);
            assertCount(rates.get(0), "qualifiedTasks", 1);
        }
    }

    @Test
    void onlyUnindicatedEvaluatedTaskHasNoIndicatorRate() throws Exception {
        try (SqlSession session = openSession("student/StudentCapacityMapper.xml")) {
            createStudentTables(session);
            execute(session, """
                    INSERT INTO edu_homework VALUES (1, 9, NULL, 60, 1, 0);
                    INSERT INTO edu_homework_assignee VALUES (1, 7, 0);
                    INSERT INTO edu_homework_record VALUES (1, 1, 7, 2, 80, 0);
                    """);

            StudentCapacityMapper mapper = session.getMapper(StudentCapacityMapper.class);
            Map<String, Object> stats = mapper.selectOverallStats(7L, List.of(9L));
            assertCount(stats, "totalTasks", 1);
            assertCount(stats, "evaluatedTasks", 1);
            assertCount(stats, "qualifiedTasks", 1);
            assertCount(stats, "unindicatedEvaluatedCount", 1);
            assertTrue(mapper.selectFirstIndicatorRates(7L, List.of(9L)).isEmpty());
        }
    }

    private SqlSession openSession(String resource) throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:s1_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE", "sa", "");
        Configuration configuration = new Configuration(new Environment("test",
                new JdbcTransactionFactory(), dataSource));
        String path = "mapper/" + resource;
        try (var input = getClass().getResourceAsStream("/" + path)) {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration).openSession();
    }

    private void createStudentTables(SqlSession session) throws Exception {
        execute(session, """
                CREATE TABLE edu_homework (
                    id BIGINT PRIMARY KEY, class_id BIGINT, indicator_first_id BIGINT,
                    pass_score INT, status INT, deleted INT);
                CREATE TABLE edu_homework_assignee (homework_id BIGINT, student_id BIGINT, deleted INT);
                CREATE TABLE edu_homework_record (
                    id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT, status INT, score INT, deleted INT);
                CREATE TABLE capacity_indicator (id BIGINT PRIMARY KEY, indicator_name VARCHAR(100), weight INT, deleted INT);
                """);
    }

    private void execute(SqlSession session, String sql) throws Exception {
        try (Statement statement = session.getConnection().createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertCount(Map<String, Object> row, String field, int expected) {
        assertEquals(expected, ((Number) row.get(field)).intValue(), field);
    }
}
