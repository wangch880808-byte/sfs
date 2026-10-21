package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** H2 MySQL 模式执行真实 Mapper、数据权限及分页拦截器，包含事务与并发争抢。 */
class EduClassCourseClaimSqlTest {
    private SqlSessionFactory factory;
    private UnpooledDataSource dataSource;
    @BeforeEach void setup() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        execute("CREATE TABLE capacity_course (id BIGINT PRIMARY KEY, deleted BOOLEAN DEFAULT 0, status INT, is_public_course INT, public_audit_status INT)");
        execute("INSERT INTO capacity_course VALUES (2,0,0,0,2),(3,1,0,0,2)");
        execute("CREATE TABLE edu_class_course (id BIGINT AUTO_INCREMENT PRIMARY KEY, class_id BIGINT, course_id BIGINT,"
                + " semester_name VARCHAR(50), semester_start_date DATE, semester_end_date DATE, assistant_teacher_ids VARCHAR(30),"
                + " status INT DEFAULT 0, backfill_pending BOOLEAN DEFAULT 0, claim_teacher_id BIGINT, claim_time TIMESTAMP,"
                + " creator VARCHAR(40), updater VARCHAR(40), create_time TIMESTAMP, update_time TIMESTAMP, deleted BOOLEAN DEFAULT 0,"
                + " UNIQUE(class_id,course_id,semester_name,deleted))");
        execute("CREATE TABLE edu_class (id BIGINT PRIMARY KEY, class_name VARCHAR(30), grade_year INT, dept_id BIGINT, education_level BIGINT,"
                + " schooling_year BIGINT, status INT, remark VARCHAR(30), creator VARCHAR(40), updater VARCHAR(40), create_time TIMESTAMP, update_time TIMESTAMP, deleted BOOLEAN)");
        execute("INSERT INTO edu_class (id,class_name,status,dept_id,deleted) VALUES (1,'一班',0,10,0),(2,'二班',0,20,0),(3,'旧班',1,10,0),(4,'删除班',0,10,1)");
        execute("CREATE TABLE edu_homework (id BIGINT PRIMARY KEY, class_id BIGINT, course_id BIGINT, semester_name VARCHAR(50), status INT, deadline TIMESTAMP, deleted BOOLEAN)");
        execute("CREATE TABLE edu_homework_record (id BIGINT PRIMARY KEY, homework_id BIGINT, status INT, deleted BOOLEAN)");
        execute("INSERT INTO edu_class_course (id,class_id,course_id,semester_name,status,creator,updater,deleted) VALUES"
                + "(11,1,2,' S ',0,'system','system',0),(12,2,2,'S',1,'system','system-sync',0),"
                + "(13,3,2,'S',1,'system','manual',0),(14,4,2,'S',0,'system','system',1)");
        var permissionApi = mock(PermissionCommonApi.class);
        var permission = new DeptDataPermissionRespDTO(); permission.setAll(false); permission.setDeptIds(Set.of(10L));
        when(permissionApi.getDeptDataPermission(7L)).thenReturn(permission);
        var rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("edu_class", "dept_id"); rule.addDeptColumn("system_users", "dept_id"); rule.addDeptColumn("system_dept", "id");
        var ruleFactory = mock(DataPermissionRuleFactory.class);
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor());
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        GlobalConfigUtils.setGlobalConfig(config, new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig())
                .setMetaObjectHandler(new DefaultDBFieldHandler()));
        config.addInterceptor(chain); config.addMapper(EduClassCourseMapper.class); config.addMapper(EduClassMapper.class);
        factory = new MybatisSqlSessionFactoryBuilder().build(config);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setUserType(2), new MockHttpServletRequest());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    @Test void claimsRestoreTrimmedRelationWithoutOverwritingUpdaterAndReleaseKeepsUpdater() {
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(EduClassCourseMapper.class);
            assertEquals(2L, mapper.lockPublicCourse(2L)); assertNull(mapper.lockPublicCourse(3L));
            assertEquals(11L, mapper.selectClaimRelation(2L, 1L, "S").getId());
            assertEquals(1, mapper.claim(11L, 7L)); assertEquals(0, mapper.claim(11L, 8L));
            var claimed = mapper.selectClaimById(11L);
            assertEquals(7L, claimed.getClaimTeacherId()); assertNotNull(claimed.getClaimTime()); assertEquals("system", claimed.getUpdater());
            assertEquals(1, mapper.claimArchived(12L, 7L)); assertEquals(1, mapper.claimArchived(13L, 7L)); assertEquals(0, mapper.claim(14L, 7L));
            assertEquals(0, mapper.selectClaimById(12L).getStatus()); assertEquals("system", mapper.selectClaimById(12L).getUpdater());
            assertEquals("manual", mapper.selectClaimById(13L).getUpdater()); assertNull(mapper.selectClaimById(14L));
            assertEquals(0, mapper.release(11L, 8L)); assertEquals(1, mapper.release(11L, 7L));
            var released = mapper.selectClaimById(11L); assertNull(released.getClaimTeacherId()); assertNull(released.getClaimTime());
            assertEquals("system", released.getUpdater()); assertEquals(0, mapper.release(14L, 7L));
        }
    }
    @Test void rollbackLeavesEarlierClaimsUnchanged() {
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduClassCourseMapper.class); mapper.lockPublicCourse(2L);
            assertEquals(1, mapper.claim(11L, 7L)); session.rollback();
        }
        try (var session = factory.openSession()) { assertNull(session.getMapper(EduClassCourseMapper.class).selectClaimById(11L).getClaimTeacherId()); }
    }
    @Test void simultaneousClaimsHaveExactlyOneWinner() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> first = contender(7L, ready, start); Callable<Integer> second = contender(8L, ready, start);
            var a = pool.submit(first); var b = pool.submit(second); assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(1, a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    private Callable<Integer> contender(Long teacherId, CountDownLatch ready, CountDownLatch start) {
        return () -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
            try (var session = factory.openSession()) {
                var mapper = session.getMapper(EduClassCourseMapper.class); mapper.lockPublicCourse(2L);
                var row = mapper.selectClaimRelation(2L, 1L, "S");
                int affected = row.getClaimTeacherId() == null ? mapper.claim(row.getId(), teacherId) : 0;
                session.commit(); return affected;
            }
        };
    }
    @Test void claimedLinksAreNeitherAutomaticallyArchivedNorRestored() throws Exception {
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(EduClassCourseMapper.class); mapper.claim(11L, 7L);
            assertEquals(0, mapper.archiveIdleAutoLinksOutsideSemester("NEXT"));
            execute("UPDATE capacity_course SET status=1 WHERE id=2");
            assertEquals(0, mapper.archiveIdleAutoLinksOfOfflineCourses(2L));
            mapper.release(11L, 7L); assertEquals(1, mapper.archiveIdleAutoLinksOfOfflineCourses(2L));
            execute("UPDATE capacity_course SET status=0 WHERE id=2");
            execute("UPDATE edu_class_course SET claim_teacher_id=8 WHERE id=12");
            assertEquals(1, mapper.restoreSyncArchivedLinks(2L, "S"));
            assertEquals(1, mapper.selectClaimById(12L).getStatus());
        }
    }
    @Test void optionsAndListWrappersExecuteWithDepartmentAndDeletionPredicates() {
        try (var session = factory.openSession(true)) {
            var classes = session.getMapper(EduClassMapper.class).selectList(new LambdaQueryWrapper<EduClassDO>().eq(EduClassDO::getStatus, 0));
            assertEquals(List.of(1L), classes.stream().map(EduClassDO::getId).toList());
            var mapper = session.getMapper(EduClassCourseMapper.class); mapper.claim(11L, 7L);
            var links = mapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>().eq(EduClassCourseDO::getCourseId, 2L)
                    .apply("TRIM(semester_name) = {0}", "S").orderByAsc(EduClassCourseDO::getStatus).orderByDesc(EduClassCourseDO::getId));
            assertEquals(3, links.size());
            var rows = mapper.selectList(new LambdaQueryWrapper<EduClassCourseDO>().eq(EduClassCourseDO::getCourseId, 2L)
                    .eq(EduClassCourseDO::getClaimTeacherId, 7L).eq(EduClassCourseDO::getStatus, 0)
                    .inSql(EduClassCourseDO::getClassId, "SELECT id FROM edu_class WHERE deleted = 0 AND status = 0")
                    .orderByDesc(EduClassCourseDO::getId));
            assertEquals(List.of(11L), rows.stream().map(EduClassCourseDO::getId).toList());
        }
    }
}
