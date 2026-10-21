package cn.nus.zknu.module.edu.service.scope;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
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
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 在真实权限、分页插件链中执行两处教师名查询，避免 CASE JOIN 绕过院系和逻辑删除。 */
class EduPublicTeacherNameSqlTest extends BaseMockitoUnitTest {
    @Mock private PermissionCommonApi permissionApi;
    @Mock private DataPermissionRuleFactory ruleFactory;
    private SqlSessionFactory factory;
    private final List<String> executed = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = ds.getConnection(); var sql = connection.createStatement()) {
            for (String statement : """
                CREATE TABLE edu_class (id BIGINT PRIMARY KEY, class_name VARCHAR(40), dept_id BIGINT, status INT, deleted INT);
                CREATE TABLE edu_class_student (id BIGINT PRIMARY KEY, class_id BIGINT, student_id BIGINT, status INT, deleted INT);
                CREATE TABLE capacity_course (id BIGINT PRIMARY KEY, course_name VARCHAR(40), teacher_Id BIGINT,
                    course_image_url VARCHAR(40), course_introduction VARCHAR(40), indicator_id BIGINT,
                    is_public_course INT, status INT, deleted INT);
                CREATE TABLE capacity_indicator (id BIGINT PRIMARY KEY, indicator_name VARCHAR(40), deleted INT);
                CREATE TABLE edu_class_course (id BIGINT PRIMARY KEY, class_id BIGINT, course_id BIGINT,
                    claim_teacher_id BIGINT, status INT, deleted INT, semester_name VARCHAR(80));
                CREATE TABLE infra_config (id BIGINT PRIMARY KEY, config_key VARCHAR(80), `value` VARCHAR(80), deleted INT);
                INSERT INTO infra_config VALUES (1,'edu.current.semester.name',' 当前学期 ',0);
                CREATE TABLE system_users (id BIGINT PRIMARY KEY, nickname VARCHAR(40), dept_id BIGINT, deleted INT);
                INSERT INTO system_users VALUES (7,'认领人',10,0),(8,'负责人',10,0),(9,'外院认领人',20,0),(10,'删除认领人',10,1),(11,'第二认领人',10,0);
                INSERT INTO edu_class VALUES (1,'本院班',10,0,0),(2,'外院班',20,0,0),(3,'删除班',10,0,1),(4,'归档班',10,1,0);
                INSERT INTO edu_class_student VALUES (1,1,100,0,0),(2,2,100,0,0),(3,3,100,0,0),(4,4,100,0,0),(5,1,101,0,1);
                INSERT INTO capacity_course (id,course_name,teacher_Id,is_public_course,status,deleted) VALUES
                    (1,'A认领公共课',8,0,0,0),(2,'B空闲公共课',8,0,0,0),(3,'C专属课',8,1,0,0),
                    (4,'D外院认领',8,0,0,0),(5,'E删除认领',8,0,0,0),(6,'F删除课程',8,0,0,1),(7,'G归档课程',8,0,1,0);
                INSERT INTO edu_class_course (id,class_id,course_id,claim_teacher_id,status,deleted) VALUES
                    (1,1,1,7,0,0),(2,1,2,NULL,0,0),(3,1,3,7,0,0),(4,1,4,9,0,0),(5,1,5,10,0,0),
                    (6,2,2,7,0,0),(7,3,2,7,0,0),(8,4,2,7,0,0),(9,1,6,7,0,0),(10,1,7,7,0,0),
                    (11,1,2,7,0,1),(12,1,2,7,1,0),(13,1,1,7,0,0);
                """.split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        var rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("edu_class", "dept_id");
        rule.addDeptColumn("system_users", "dept_id");
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var permission = new DeptDataPermissionRespDTO();
        permission.setAll(false); permission.setDeptIds(Set.of(10L));
        when(permissionApi.getDeptDataPermission(100L)).thenReturn(permission);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(100L).setUserType(2), new MockHttpServletRequest());
        var chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor());
        chain.addInnerInterceptor(new InnerInterceptor() {
            @Override
            public void beforePrepare(StatementHandler handler, Connection connection, Integer timeout) {
                executed.add(PluginUtils.mpStatementHandler(handler).mPBoundSql().sql());
            }
        });
        configuration.addInterceptor(chain);
        configuration.addMapper(EduMessageMapper.class);
        String resource = "mapper/student/StudentCourseMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentCourseListUsesClaimantAndOwnerFallbackInOneSql() {
        try (var session = factory.openSession()) {
            var rows = session.getMapper(StudentCourseMapper.class).selectCoursesByClassIds(List.of(1L, 2L, 3L, 4L));
            assertEquals(5, rows.size());
            var names = new HashMap<Long, String>();
            rows.forEach(row -> names.put(row.getCourseId(), row.getTeacherName()));
            assertEquals("认领人", names.get(1L));
            assertEquals("负责人", names.get(2L));
            assertEquals("负责人", names.get(3L));
            assertNull(names.get(4L));
            assertNull(names.get(5L));
            // 只按任务要求修改姓名，原 teacherId 字段继续表示课程负责人。
            assertTrue(rows.stream().allMatch(row -> row.getTeacherId().equals(8L)));
        }
        assertEquals(1, executed.size());
        assertTrue(executed.get(0).contains("teacher.dept_id"));
        assertTrue(executed.get(0).contains("ec.dept_id"));
    }

    @Test
    void studentConversationsKeepGroupingOrderAndTeacherVisibility() {
        try (var session = factory.openSession()) {
            var rows = session.getMapper(EduMessageMapper.class).selectStudentConversations(100L);
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L), rows.stream().map(row -> row.getCourseId()).toList());
            assertEquals(Arrays.asList("认领人", "负责人", "负责人", null, null),
                    rows.stream().map(row -> row.getTeacherName()).toList());
            assertTrue(session.getMapper(EduMessageMapper.class).selectStudentConversations(101L).isEmpty());
        }
        assertEquals(2, executed.size());
        assertTrue(executed.get(0).contains("teacher.dept_id"));
        assertTrue(executed.get(0).contains("cl.dept_id"));
    }
    @Test
    void currentSemesterWinsOverNewerOtherSemesterAndUnclaimedRows() throws Exception {
        addLinks("""
            INSERT INTO edu_class_course VALUES (20,1,1,11,0,0,' 当前学期'),
                (21,1,1,7,0,0,'其他学期'),(22,1,1,NULL,0,0,'当前学期'),
                (23,1,1,7,1,0,'当前学期'),(24,1,1,7,0,1,'当前学期');
            """);
        assertCourseAndConversationTeacher(1L, "第二认领人");
    }

    @Test
    void latestClaimWinsWhenCurrentSemesterHasNoClaimAndOwnerIsUsedOnlyWhenAllAreUnclaimed() throws Exception {
        addLinks("""
            INSERT INTO edu_class_course VALUES (20,1,1,11,0,0,'旧学期'),
                (21,1,1,NULL,0,0,'当前学期');
            """);
        assertCourseAndConversationTeacher(1L, "第二认领人");
        assertCourseAndConversationTeacher(2L, "负责人");
    }

    @Test
    void multipleVisibleClassesKeepOneCourseAndOneConversationPerClass() throws Exception {
        addLinks("""
            INSERT INTO edu_class VALUES (5,'第二本院班',10,0,0);
            INSERT INTO edu_class_student VALUES (6,5,100,0,0);
            INSERT INTO edu_class_course VALUES (20,5,1,8,0,0,'当前学期'),
                (21,1,1,8,0,0,'旧学期'),(22,1,1,7,0,0,'当前学期');
            """);
        try (var session = factory.openSession()) {
            var courses = session.getMapper(StudentCourseMapper.class).selectCoursesByClassIds(List.of(1L, 5L));
            assertEquals(5, courses.size());
            assertEquals("认领人", courses.stream().filter(row -> row.getCourseId().equals(1L)).findFirst().orElseThrow().getTeacherName());
            var threads = session.getMapper(EduMessageMapper.class).selectStudentConversations(100L).stream()
                    .filter(row -> row.getCourseId().equals(1L)).toList();
            assertEquals(2, threads.size());
            assertEquals(Map.of(1L, "认领人", 5L, "负责人"), threads.stream().collect(Collectors.toMap(
                    row -> row.getClassId(), row -> row.getTeacherName())));
        }
        assertEquals(2, executed.size());
    }

    @Test
    void missingOrDeletedCurrentSemesterConfigUsesLatestClaim() throws Exception {
        addLinks("""
            UPDATE infra_config SET deleted = 1;
            INSERT INTO edu_class_course VALUES (20,1,1,11,0,0,'旧学期');
            """);
        assertCourseAndConversationTeacher(1L, "第二认领人");
    }

    private void addLinks(String statements) throws Exception {
        try (var connection = factory.getConfiguration().getEnvironment().getDataSource().getConnection();
             var sql = connection.createStatement()) {
            for (var statement : statements.split(";")) if (!statement.isBlank()) sql.execute(statement);
        }
    }

    private void assertCourseAndConversationTeacher(Long courseId, String expected) {
        try (var session = factory.openSession()) {
            var courses = session.getMapper(StudentCourseMapper.class).selectCoursesByClassIds(List.of(1L));
            assertEquals(5, courses.size());
            assertEquals(expected, courses.stream().filter(row -> row.getCourseId().equals(courseId))
                    .findFirst().orElseThrow().getTeacherName());
            var threads = session.getMapper(EduMessageMapper.class).selectStudentConversations(100L);
            assertEquals(5, threads.size());
            assertEquals(expected, threads.stream().filter(row -> row.getCourseId().equals(courseId))
                    .findFirst().orElseThrow().getTeacherName());
        }
    }

}
