package cn.nus.zknu.module.edu.service.dataexchange;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageStudentListRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO;
import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.mysql.importbatch.EduImportBatchItemMapper;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseBatchMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.annotation.DbType;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 在 H2 MySQL 模式走真实数据权限、分页拦截器，按旧查询对照批量结果。 */
class C3QuerySqlTest {
    private SqlSessionFactory factory;
    private PermissionCommonApi permissionApi;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "");
        try (var connection = ds.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE system_users (id BIGINT PRIMARY KEY, username VARCHAR(30), password VARCHAR(30), "
                    + "password_change_required BOOLEAN, user_type INT, nickname VARCHAR(30), remark VARCHAR(30), "
                    + "dept_id BIGINT, post_ids VARCHAR(100), email VARCHAR(30), mobile VARCHAR(30), sex INT, "
                    + "avatar VARCHAR(100), status INT, login_ip VARCHAR(30), login_date TIMESTAMP, "
                    + "create_time TIMESTAMP, update_time TIMESTAMP, creator VARCHAR(30), updater VARCHAR(30), deleted BOOLEAN)");
            sql.execute("INSERT INTO system_users (id,username,nickname,mobile,user_type,dept_id,status,deleted) VALUES "
                    + "(1,'20261234','张小明','13812340000',2,10,0,0),"
                    + "(2,'20261235','张明','13812350000',2,10,0,0),"
                    + "(3,'20261236','张三','13812360000',2,20,0,0),"
                    + "(4,'teacher','教师','13900000000',1,10,0,0),"
                    + "(5,'archived','归档学生','13900000001',2,10,1,0),"
                    + "(6,'deleted','删除学生','13900000002',2,10,0,1)");
            sql.execute("CREATE TABLE edu_class (id BIGINT PRIMARY KEY, dept_id BIGINT, status INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_class VALUES (100,10,0,0),(200,20,0,0),(300,10,1,0),(400,10,0,1)");
            sql.execute("CREATE TABLE capacity_course (id BIGINT PRIMARY KEY, status INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO capacity_course VALUES (10,0,0),(20,0,0),(30,1,0),(40,0,1),(50,0,0)");
            sql.execute("CREATE TABLE edu_class_course (id BIGINT PRIMARY KEY, class_id BIGINT, course_id BIGINT, "
                    + "semester_name VARCHAR(30), status INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_class_course VALUES (1,100,10,' 2026 ',0,0),(2,100,20,'',0,0),"
                    + "(3,200,10,'2026',0,0),(4,300,10,'2026',0,0),(5,100,30,'2026',0,0),"
                    + "(6,100,40,'2026',0,0),(7,100,50,'2026',1,0),(8,400,10,'2026',0,0)");
            sql.execute("CREATE TABLE edu_homework (id BIGINT PRIMARY KEY, course_id BIGINT, class_id BIGINT, "
                    + "homework_name VARCHAR(30), homework_type INT, homework_level INT, deadline TIMESTAMP, "
                    + "score INT, pass_score INT, semester_name VARCHAR(30), status INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework (id,course_id,class_id,homework_name,score,pass_score,semester_name,status,deleted) VALUES "
                    + "(1,10,100,'及格',100,60,'2026',1,0),(2,10,100,'打回',100,60,'',2,0),"
                    + "(3,10,100,'重交',100,60,NULL,1,0),(4,10,100,'空成绩',100,60,'2026',2,0),"
                    + "(5,20,100,'空班课学期',100,60,'2025',1,0),(6,20,100,'未交',100,60,'',2,0),"
                    + "(7,10,200,'外院',100,60,'2026',1,0),(8,10,100,'错学期',100,60,'2025',1,0),"
                    + "(9,10,100,'归档任务',100,60,'2026',3,0),(10,10,100,'删除任务',100,60,'2026',1,1),"
                    + "(11,10,300,'归档班级',100,60,'2026',1,0),(12,30,100,'归档课程',100,60,'2026',1,0),"
                    + "(13,40,100,'删除课程',100,60,'2026',1,0),(14,50,100,'归档班课',100,60,'2026',1,0),"
                    + "(15,10,100,'移除分配',100,60,'2026',1,0),(16,10,400,'删除班级',100,60,'2026',1,0)");
            sql.execute("CREATE TABLE edu_homework_assignee (id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT, deleted BOOLEAN)");
            for (int id = 1; id <= 16; id++) {
                sql.execute("INSERT INTO edu_homework_assignee VALUES (" + id + "," + id + ",1," + (id == 15 ? 1 : 0) + ")");
            }
            sql.execute("CREATE TABLE edu_homework_record (id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT, "
                    + "status INT, score INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework_record VALUES (1,1,1,2,80,0),(2,2,1,3,40,0),(3,3,1,4,50,0),"
                    + "(4,4,1,2,NULL,0),(5,5,1,2,70,0),(6,6,1,2,100,1),(7,7,1,2,90,0),(8,15,1,2,99,0)");
            sql.execute("CREATE TABLE edu_message (id BIGINT PRIMARY KEY, class_id BIGINT, course_id BIGINT, "
                    + "student_id BIGINT, type INT, read_status INT, content VARCHAR(30), create_time TIMESTAMP, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_message VALUES (1,100,10,1,0,0,'首条','2026-01-01',0),"
                    + "(2,100,10,1,0,NULL,'旧空状态','2026-02-01',0),(3,100,10,1,1,0,'教师回复','2026-03-01',0),"
                    + "(4,100,10,2,0,1,'已读','2026-04-01',0),(5,100,10,1,0,0,'已删除最新','2026-05-01',1),"
                    + "(6,200,10,1,0,0,'其他班级','2026-06-01',0),(7,100,20,1,0,0,'其他课程','2026-07-01',0),"
                    + "(8,100,10,2,0,2,'按编号最新','2025-01-01',0)");
            sql.execute("CREATE TABLE edu_import_batch_item (id BIGINT PRIMARY KEY, batch_id BIGINT, row_number INT, "
                    + "normalized_data VARCHAR(100), validation_status VARCHAR(30), action VARCHAR(30), deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_import_batch_item VALUES (1,1,2,'{\"skipReason\":\"UNCHANGED\"}','VALID','SKIP',0),"
                    + "(2,1,1,'broken json','VALID','SKIP',0),(3,1,3,'invalid','INVALID','SKIP',0),"
                    + "(4,1,4,'update','VALID','UPDATE',0),(5,1,5,'deleted','VALID','SKIP',1),(6,2,6,'other','VALID','SKIP',0)");
        }
        permissionApi = mock(PermissionCommonApi.class);
        var rules = mock(DataPermissionRuleFactory.class);
        var rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("system_users", "dept_id");
        rule.addDeptColumn("edu_class", "dept_id");
        rule.addDeptColumn("system_dept", "id");
        when(rules.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        var chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(rules)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(chain);
        config.addMapper(AdminUserMapper.class);
        config.addMapper(EduMessageMapper.class);
        config.addMapper(EduImportBatchItemMapper.class);
        for (String resource : List.of("mapper/student/StudentCourseMapper.xml", "mapper/student/StudentCourseBatchMapper.xml")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        factory = new MybatisSqlSessionFactoryBuilder().build(config);
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    private void login(boolean all) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(99L).setUserType(2), new MockHttpServletRequest());
        var scope = new DeptDataPermissionRespDTO();
        scope.setAll(all);
        scope.setDeptIds(Set.of(10L));
        when(permissionApi.getDeptDataPermission(99L)).thenReturn(scope);
    }

    @Test
    void batchProgressAndHomeworkMatchOldQueriesUnderCollegeAndSchoolScopes() {
        for (boolean all : List.of(false, true)) {
            login(all);
            try (var session = factory.openSession()) {
                var old = session.getMapper(StudentCourseMapper.class);
                var batch = session.getMapper(StudentCourseBatchMapper.class);
                var progress = batch.selectCourseProgressBatch(1L, List.of(10L,20L,30L,40L,50L));
                assertEquals(List.of(10L,20L), progress.stream().map(row -> row.getCourseId()).sorted().toList());
                for (var row : progress) {
                    var expected = old.selectCourseProgress(1L, row.getCourseId(), false);
                    assertEquals(((Number) expected.get("totalHomework")).intValue(), row.getTotalHomework());
                    assertEquals(((Number) expected.get("completedHomework")).intValue(), row.getCompletedHomework());
                    assertEquals(((Number) expected.get("avgScore")).doubleValue(), row.getAvgScore(), 0.000001);
                }
                var tasks = batch.selectCourseHomeworksBatch(1L, List.of(10L,20L,30L,40L,50L));
                for (long courseId : List.of(10L,20L,30L,40L,50L)) {
                    assertEquals(old.selectCourseHomeworks(1L, courseId, false), tasks.stream()
                            .filter(row -> row.getCourseId() == courseId).map(row -> row.getHomework()).toList());
                }
                assertEquals(all ? List.of(7L,6L,5L,4L,3L,2L,1L) : List.of(6L,5L,4L,3L,2L,1L),
                        tasks.stream().map(row -> row.getHomework().getHomeworkId()).toList());
            }
        }
    }

    @Test
    void teacherSummariesUseLatestIdAndOldUnreadNullSemantics() {
        login(false);
        try (var session = factory.openSession()) {
            var rows = session.getMapper(EduMessageMapper.class).selectTeacherStudentSummaries(100L,10L);
            assertEquals(List.of(2L,1L), rows.stream().map(row -> row.getStudentId()).toList());
            assertEquals(List.of(1,2), rows.stream().map(row -> row.getUnreadCount()).toList());
            assertEquals(List.of("按编号最新","教师回复"), rows.stream().map(row -> row.getLastMessageContent()).toList());
            var raw = session.getMapper(EduMessageMapper.class).selectList(
                    new LambdaQueryWrapper<EduMessageDO>()
                            .select(EduMessageDO::getId,
                                    EduMessageDO::getStudentId,
                                    EduMessageDO::getType,
                                    EduMessageDO::getReadStatus,
                                    EduMessageDO::getContent,
                                    EduMessageDO::getCreateTime)
                            .eq(EduMessageDO::getClassId,100L)
                            .eq(EduMessageDO::getCourseId,10L)
                            .orderByDesc(EduMessageDO::getId));
            var expected = new java.util.LinkedHashMap<Long, MessageStudentListRespVO>();
            for (var message : raw) {
                var row = expected.computeIfAbsent(message.getStudentId(), id -> {
                    var item = new MessageStudentListRespVO();
                    item.setStudentId(id); item.setUnreadCount(0);
                    item.setLastMessageTime(message.getCreateTime()); item.setLastMessageContent(message.getContent());
                    return item;
                });
                if (Integer.valueOf(0).equals(message.getType()) && !Integer.valueOf(1).equals(message.getReadStatus())) {
                    row.setUnreadCount(row.getUnreadCount() + 1);
                }
            }
            assertEquals(List.copyOf(expected.values()), rows);
            assertTrue(session.getMapper(EduMessageMapper.class).selectTeacherStudentSummaries(999L,10L).isEmpty());
        }
    }

    @Test
    void simpleListProjectsOnlyResponseColumnsAndKeepsCollegePermission() {
        login(false);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(AdminUserMapper.class);
            assertEquals(List.of(1L,2L), mapper.selectSimpleList(0,2).stream().map(row -> row.getId()).toList());
            assertEquals(List.of(1L,2L,4L), mapper.selectSimpleList(0,null).stream().map(row -> row.getId()).toList());
            assertTrue(mapper.selectSimpleList(0,99).isEmpty());
            var row = mapper.selectSimpleList(0,2).get(0);
            assertEquals("张小明", row.getNickname());
            assertEquals(10L, row.getDeptId());
            assertNull(row.getUsername());
            assertNull(row.getPassword());
        }
        login(true);
        try (var session = factory.openSession()) {
            assertEquals(List.of(1L,2L,3L), session.getMapper(AdminUserMapper.class)
                    .selectSimpleList(0,2).stream().map(row -> row.getId()).toList());
        }
    }

    @Test
    void userPageCountKeepsContainsSearchAndScopeWithPagination() {
        login(false);
        try (var session = factory.openSession()) {
            var req = new UserPageReqVO();
            req.setUserType(2); req.setStatus(0); req.setUsername("123"); req.setNickname("明"); req.setMobile("123");
            req.setPageNo(1); req.setPageSize(1);
            var mapper = session.getMapper(AdminUserMapper.class);
            var first = mapper.selectPage(req, List.of(10L,20L), null);
            assertEquals(2L, first.getTotal());
            assertEquals(List.of(2L), first.getList().stream().map(row -> row.getId()).toList());
            req.setPageNo(2);
            assertEquals(List.of(1L), mapper.selectPage(req, List.of(10L,20L), null)
                    .getList().stream().map(row -> row.getId()).toList());
            req.setPageNo(3);
            assertTrue(mapper.selectPage(req, List.of(10L,20L), null).getList().isEmpty());
            req.setPageNo(1);
            assertEquals(1L, mapper.selectPage(req, List.of(10L), List.of(1L)).getTotal());
        }
    }

    @Test
    void skipReasonRowsFilterInSqlAndDoNotNeedOtherLargeColumns() {
        login(false);
        try (var session = factory.openSession()) {
            var rows = session.getMapper(EduImportBatchItemMapper.class).selectSkipReasonRows(1L);
            assertEquals(List.of("broken json", "{\"skipReason\":\"UNCHANGED\"}"),
                    rows.stream().map(row -> row.getNormalizedData()).toList());
            assertNull(rows.get(0).getId());
            assertTrue(session.getMapper(EduImportBatchItemMapper.class).selectSkipReasonRows(99L).isEmpty());
        }
    }
}
