package cn.nus.zknu.module.edu.service.coursemanage;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
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
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** H2 MySQL 模式执行 XML 与真实权限、分页拦截器链，对照原 Java 统计算法。 */
class EduCourseStatMapperSqlTest extends BaseMockitoUnitTest {
    @Mock private PermissionCommonApi permissionApi;
    @Mock private DataPermissionRuleFactory ruleFactory;
    private SqlSessionFactory factory;
    private UnpooledDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE edu_homework_assignee (id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT NOT NULL, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework_assignee VALUES (1,101,1,0),(2,101,2,0),(3,101,3,0),"
                    + "(4,101,4,0),(5,101,5,1),(6,101,1,0),(8,102,1,0),(9,103,1,0),(10,104,1,0),"
                    + "(11,105,1,0),(12,105,2,0),(13,105,3,0)");
            // 不建正文和评语列，确保统计路径只读取必要字段。
            sql.execute("CREATE TABLE edu_homework_record (id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT NOT NULL, status INT, score INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework_record VALUES (1,101,1,2,80,0),(2,101,2,3,70,0),"
                    + "(3,101,3,4,NULL,0),(4,101,4,0,30,0),(5,101,5,2,100,0),(6,101,6,2,100,0),"
                    + "(7,101,1,2,99,1),(8,101,1,1,90,0),(9,101,4,NULL,NULL,0),"
                    + "(11,102,1,2,NULL,0),(12,103,1,2,60,0),(13,104,1,2,50,0),"
                    + "(14,105,1,0,0,0),(15,105,2,3,0,0),(16,105,3,4,1,0)");
            sql.execute("CREATE TABLE edu_class_student (id BIGINT PRIMARY KEY, class_id BIGINT, student_id BIGINT NOT NULL, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_class_student VALUES (1,21,1,0),(2,21,1,0),(3,21,2,1),(4,22,3,0)");
            sql.execute("CREATE TABLE edu_homework (id BIGINT PRIMARY KEY, course_id BIGINT, teacher_id BIGINT, status INT, deleted BOOLEAN, "
                    + "class_id BIGINT, semester_name VARCHAR(100), homework_name VARCHAR(100), homework_type INT, homework_level INT, "
                    + "indicator_first_id BIGINT, indicator_second_id BIGINT, requirement VARCHAR(100), deadline TIMESTAMP, auto_grade INT, "
                    + "submission_mode VARCHAR(100), ai_review_mode INT, required_attachment_types_json VARCHAR(100), ai_rubric_json VARCHAR(100), "
                    + "ai_reference_json VARCHAR(100), ai_confidence_threshold DOUBLE, pass_score INT, score INT, status_before_archive INT, "
                    + "source_homework_id BIGINT, creator VARCHAR(100), updater VARCHAR(100), create_time TIMESTAMP, update_time TIMESTAMP)");
            sql.execute("INSERT INTO edu_homework (id,course_id,teacher_id,status,deleted,class_id,semester_name) VALUES "
                    + "(101,1,7,1,0,21,NULL),(102,1,8,2,0,21,''),(103,1,7,3,0,21,'本学期'),"
                    + "(104,1,7,1,1,21,'本学期'),(105,1,7,0,0,21,'本学期')");
        }
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("system_users", "dept_id");
        rule.addDeptColumn("system_dept", "id");
        rule.addDeptColumn("edu_class", "dept_id");
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        MybatisPlusInterceptor chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(chain);
        String resource = "mapper/coursemanage/EduCourseStatMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        configuration.addMapper(EduHomeworkMapper.class);
        factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setUserType(2), new MockHttpServletRequest());
        var permission = new DeptDataPermissionRespDTO();
        permission.setAll(false);
        permission.setDeptIds(Set.of(10L));
        lenient().when(permissionApi.getDeptDataPermission(7L)).thenReturn(permission);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assigneeAndClassCountsKeepRowCountRatherThanDistinctCount() {
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduCourseStatMapper.class);
            assertEquals(Map.of(101L, 5L, 102L, 1L), mapper.selectAssigneeCounts(List.of(101L, 102L)).stream()
                    .collect(Collectors.toMap(EduCourseStatMapper.HomeworkCount::getHomeworkId,
                            EduCourseStatMapper.HomeworkCount::getStudentCount)));
            assertEquals(Map.of(21L, 2, 22L, 1), mapper.selectClassStudentCounts(List.of(21L, 22L, 23L)).stream()
                    .collect(Collectors.toMap(EduCourseStatMapper.ClassCount::getClassId,
                            EduCourseStatMapper.ClassCount::getStudentCount)));
        }
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduCourseStatMapper.class.getName() + ".selectAssigneeCounts");
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduCourseStatMapper.class.getName() + ".selectClassStudentCounts");
    }

    @Test
    void groupedStatsAndProjectedRecordsMatchOriginalFilteringAndStatusRules() throws Exception {
        List<Row> original = originalRecords(101L);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduCourseStatMapper.class);
            var stats = mapper.selectRecordStats(List.of(101L, 102L));
            var stat = stats.stream().filter(item -> item.getHomeworkId() == 101L).findFirst().orElseThrow();
            assertEquals(original.stream().filter(r -> r.status != null && r.status >= 1).count(), stat.getSubmittedCount());
            assertEquals(original.stream().filter(r -> Objects.equals(r.status, 2)).count(), stat.getCorrectedCount());
            assertEquals(original.stream().filter(r -> r.score != null).mapToInt(r -> r.score).average().orElseThrow(), stat.averageScore());
            assertEquals(4, stat.getSubmittedCount());
            assertEquals(1, stat.getCorrectedCount());
            assertEquals(67.5, stat.averageScore());
            assertNull(stats.stream().filter(item -> item.getHomeworkId() == 102L).findFirst().orElseThrow().averageScore());
            var projected = mapper.selectAssignedRecords(List.of(101L));
            assertEquals(original.size(), projected.size());
            assertEquals(original.stream().map(r -> r.studentId + ":" + r.status + ":" + r.score).sorted().toList(),
                    projected.stream().map(r -> r.getStudentId() + ":" + r.getStatus() + ":" + r.getScore()).sorted().toList());
            assertTrue(projected.stream().allMatch(r -> r.getContent() == null && r.getComment() == null));
        }
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduCourseStatMapper.class.getName() + ".selectRecordStats");
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduCourseStatMapper.class.getName() + ".selectAssignedRecords");
    }

    @Test
    void homeworkStatisticsMatchOriginalDistinctStudentsAndUnroundedScores() throws Exception {
        List<Row> original = originalRecords(101L);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduCourseStatMapper.class);
            var stat = mapper.selectHomeworkStatistics(101L, 60);
            assertEquals(4, stat.getTotalCount());
            assertEquals(distinct(original, r -> r.status != null && r.status >= 1), stat.getSubmittedCount());
            assertEquals(distinct(original, r -> Objects.equals(r.status, 2)), stat.getCorrectedCount());
            assertEquals(distinct(original, r -> Objects.equals(r.status, 3)), stat.getRedoCount());
            assertEquals(distinct(original, r -> r.score != null && r.score >= 60), stat.getPassCount());
            assertEquals(67.5, stat.averageScore());
            assertEquals(90, stat.getMaxScore());
            assertEquals(30, stat.getMinScore());
            assertEquals(1D / 3, mapper.selectHomeworkStatistics(105L, 1).averageScore());
            var empty = mapper.selectHomeworkStatistics(999L, 60);
            assertEquals(0, empty.getTotalCount());
            assertEquals(0, empty.getSubmittedCount());
            assertNull(empty.averageScore());
            assertNull(empty.getMaxScore());
            assertNull(mapper.selectHomeworkStatistics(102L, 60).averageScore());
        }
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduCourseStatMapper.class.getName() + ".selectHomeworkStatistics");
    }

    @Test
    void courseServiceIncludesArchivedAndDraftTasksButExcludesDeletedRows() {
        try (var session = factory.openSession()) {
            var service = serviceWithRealHomeworkQueries(session);
            var detail = service.getCourseDetail(1L);
            var rows = detail.getTasks();
            assertEquals(List.of(105L, 103L, 102L, 101L), rows.stream()
                    .map(cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageDetailRespVO.CourseTaskRespVO::getHomeworkId).toList());
            assertEquals(List.of(0, 3, 2, 1), rows.stream()
                    .map(cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageDetailRespVO.CourseTaskRespVO::getStatus).toList());
            // 待发布任务故意存在历史分配和成绩，仍不得展示为已发布统计。
            assertEquals(0, rows.get(0).getStudentCount());
            assertEquals(0, rows.get(0).getSubmittedCount());
            assertEquals(0, rows.get(0).getCorrectedCount());
            assertEquals(0D, rows.get(0).getAvgScore());
            assertEquals(1, rows.get(1).getStudentCount());
            assertEquals(1, rows.get(1).getSubmittedCount());
            assertEquals(1, rows.get(1).getCorrectedCount());
            assertEquals(60D, rows.get(1).getAvgScore());
            assertFalse(rows.get(1).getCanGrade());
            assertTrue(rows.get(1).getCanManage());
        }
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduHomeworkMapper.class.getName() + ".selectList");
    }

    @Test
    void classServiceQueryExcludesDraftAndDeletedTasksWhileKeepingArchivedAndSemesterFallback() throws Exception {
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO edu_homework (id,course_id,teacher_id,status,deleted,class_id,semester_name) VALUES "
                    + "(106,1,7,1,0,21,'旧学期'),(107,1,7,1,0,22,'本学期'),(108,2,7,1,0,21,'本学期')");
        }
        try (var session = factory.openSession()) {
            var service = new EduCourseManageServiceImpl();
            var scope = mock(cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService.class);
            when(scope.isGlobalAdmin()).thenReturn(true);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "dataScopeService", scope);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "capacityCourseApi",
                    mock(cn.nus.zknu.module.capacity.api.course.CapacityCourseApi.class));
            org.springframework.test.util.ReflectionTestUtils.setField(service, "eduHomeworkMapper", session.getMapper(EduHomeworkMapper.class));
            var binding = new cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO();
            binding.setCourseId(1L); binding.setClassId(21L); binding.setSemesterName("本学期");
            var course = new cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO();
            course.setId(1L); course.setTeacherId(7L);
            List<EduHomeworkDO> tasks = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service, "buildClassHomeworkList", binding, course, 7L);
            assertNotNull(tasks);
            assertEquals(List.of(103L, 102L, 101L), tasks.stream().map(EduHomeworkDO::getId).toList());
            when(scope.isGlobalAdmin()).thenReturn(false);
            tasks = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildClassHomeworkList", binding, course, 9L);
            assertNotNull(tasks);
            assertTrue(tasks.isEmpty());
        }
        verify(ruleFactory, atLeastOnce()).getDataPermissionRule(EduHomeworkMapper.class.getName() + ".selectList");
    }

    private EduCourseManageServiceImpl serviceWithRealHomeworkQueries(org.apache.ibatis.session.SqlSession session) {
        var service = new EduCourseManageServiceImpl();
        var course = new cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO();
        course.setId(1L); course.setTeacherId(7L);
        var capacityApi = mock(cn.nus.zknu.module.capacity.api.course.CapacityCourseApi.class);
        when(capacityApi.getCourse(1L)).thenReturn(course);
        var scope = mock(cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService.class);
        when(scope.isGlobalAdmin()).thenReturn(true);
        var permissions = mock(cn.nus.zknu.framework.security.core.service.SecurityFrameworkService.class);
        when(permissions.hasPermission(anyString())).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "capacityCourseApi", capacityApi);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dataScopeService", scope);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "securityFrameworkService", permissions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "eduHomeworkMapper", session.getMapper(EduHomeworkMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "courseStatMapper", session.getMapper(EduCourseStatMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "eduClassCourseMapper",
                mock(cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "assistantService",
                mock(cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adminUserMapper",
                mock(cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "eduCourseResourceMapper",
                mock(cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper.class));
        return service;
    }

    @Test
    void emptyCollectionsNeverProduceInvalidInOrReadUnrelatedRows() {
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduCourseStatMapper.class);
            assertTrue(mapper.selectAssigneeCounts(List.of()).isEmpty());
            assertTrue(mapper.selectClassStudentCounts(List.of()).isEmpty());
            assertTrue(mapper.selectRecordStats(List.of()).isEmpty());
            assertTrue(mapper.selectAssignedRecords(List.of()).isEmpty());
        }
    }

    private List<Row> originalRecords(long homeworkId) throws Exception {
        Set<Long> assigned = new HashSet<>();
        List<Row> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT student_id FROM edu_homework_assignee WHERE deleted = 0 AND homework_id = " + homeworkId)) {
                while (rows.next()) {
                    Long id = (Long) rows.getObject(1);
                    assigned.add(id);
                }
            }
            try (var rows = sql.executeQuery("SELECT student_id, status, score FROM edu_homework_record WHERE deleted = 0 AND homework_id = " + homeworkId)) {
                while (rows.next()) {
                    Long id = (Long) rows.getObject(1);
                    if (assigned.contains(id)) records.add(new Row(id, (Integer) rows.getObject(2), (Integer) rows.getObject(3)));
                }
            }
        }
        return records;
    }

    private long distinct(List<Row> records, java.util.function.Predicate<Row> predicate) {
        return records.stream().filter(predicate).map(Row::studentId).distinct().count();
    }

    private record Row(Long studentId, Integer status, Integer score) { }
}
