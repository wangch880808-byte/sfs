package cn.nus.zknu.module.edu.service.classstudent;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.common.util.spring.SpringUtils;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.*;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.*;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.*;
import cn.nus.zknu.module.system.dal.mysql.user.*;
import cn.nus.zknu.module.system.dal.mysql.dept.UserPostMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.oauth2.OAuth2TokenService;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.permission.RoleService;
import cn.nus.zknu.module.system.service.dept.PostService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.user.AdminUserServiceImpl;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import cn.nus.zknu.module.system.service.user.classstudent.UserClassStudentServiceImpl;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtServiceImpl;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 用真实用户创建/编辑、Spring 同步事件、事务和 H2 MyBatis 链验证分配快照。 */
class EduStudentMembershipSyncSqlTest {

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig { }

    private static final Map<Class<?>, Class<?>> TABLES = Map.of(
            AdminUserMapper.class, AdminUserDO.class,
            UserProfileMapper.class, UserProfileDO.class,
            EduClassStudentRelMapper.class, EduClassStudentRelDO.class,
            EduClassMapper.class, EduClassDO.class,
            EduHomeworkMapper.class, EduHomeworkDO.class,
            EduHomeworkRecordMapper.class, EduHomeworkRecordDO.class,
            EduHomeworkAssigneeMapper.class, EduHomeworkAssigneeDO.class);

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private AdminUserService users;
    private EduBusinessNotifyService notifications;
    private DataPermissionRuleFactory rules;

    @BeforeEach
    void setUp() throws Exception {
        com.mzt.logapi.context.LogRecordContext.putEmptySpan();
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        sql = new JdbcTemplate(ds);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), ds));
        var global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
        global.setMetaObjectHandler(new DefaultDBFieldHandler());
        GlobalConfigUtils.setGlobalConfig(config, global);

        PermissionCommonApi permissions = mock(PermissionCommonApi.class);
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setAll(false); permission.setDeptIds(Set.of(10L));
        when(permissions.getDeptDataPermission(7L)).thenReturn(permission);
        var rule = new DeptDataPermissionRule(permissions);
        rule.addDeptColumn("system_users", "dept_id");
        rule.addDeptColumn("system_dept", "id");
        rule.addDeptColumn("edu_class", "dept_id");
        rules = mock(DataPermissionRuleFactory.class);
        when(rules.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(rules)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(chain);
        TABLES.keySet().forEach(config::addMapper);
        String xml = "mapper/classstudent/EduClassStudentBatchMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(xml)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, xml, config.getSqlFragments()).parse();
        }
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        var template = new SqlSessionTemplate(factory);
        for (Class<?> type : TABLES.values()) {
            var info = TableInfoHelper.getTableInfo(type);
            String fields = info.getFieldList().stream()
                    .map(field -> field.getColumn() + " " + sqlType(field))
                    .collect(java.util.stream.Collectors.joining(","));
            sql.execute("CREATE TABLE " + info.getTableName()
                    + " (id BIGINT AUTO_INCREMENT PRIMARY KEY," + fields + ")");
        }
        sql.execute("CREATE UNIQUE INDEX uk_assignment ON edu_homework_assignee (homework_id, student_id)");
        sql.execute("CREATE UNIQUE INDEX uk_class_student ON edu_class_student (class_id, student_id)");

        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfig.class, SpringUtils.class, AdminUserServiceImpl.class,
                UserProfileExtServiceImpl.class, UserClassStudentServiceImpl.class,
                EduClassStudentServiceImpl.class, EduStudentClassesChangedListener.class);
        context.registerBean(DataSource.class, () -> ds);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(ds));
        context.registerBean(SqlSessionTemplate.class, () -> template);
        for (Class<?> mapper : TABLES.keySet()) registerMapper(mapper, template);
        registerMapper(EduClassStudentBatchMapper.class, template);
        registerMock(UserDeptScopeService.class);
        registerMock(EduDeptScopeService.class);
        registerMock(DeptService.class);
        registerMock(PostService.class);
        registerMock(RoleService.class);
        registerMock(PermissionService.class);
        registerMock(EduRoleApi.class);
        registerMock(OAuth2TokenService.class);
        registerMock(UserPostMapper.class);
        registerMock(EduClassStudentMapper.class);
        PasswordEncoder encoder = registerMock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("测试密码哈希");
        notifications = registerMock(EduBusinessNotifyService.class);
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "通知前快照必须仍在原事务内");
            return 1;
        }).when(notifications).sendToUsers(any(), anyString(), anyMap(), anyString());
        context.refresh();
        users = context.getBean(AdminUserService.class);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setUserType(2), new MockHttpServletRequest());

        sql.update("INSERT INTO edu_class (id,class_name,dept_id,grade_year,deleted) VALUES "
                + "(10,'原班',10,2026,0),(20,'新班',10,2026,0)");
        task(40L, 10L, 1, LocalDateTime.now().plusDays(1), false);
        task(41L, 10L, 1, null, false);
        task(42L, 10L, 1, LocalDateTime.now().minusDays(1), false);
        task(43L, 10L, 3, LocalDateTime.now().plusDays(1), false);
        task(44L, 10L, 0, null, false);
        task(45L, 10L, 2, LocalDateTime.now().plusDays(1), false);
        task(46L, 10L, 1, null, true);
        task(50L, 20L, 1, LocalDateTime.now().plusDays(1), false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        com.mzt.logapi.context.LogRecordContext.clear();
        if (context != null) context.close();
    }

    @Test
    void creatingStudentWithClassImmediatelyAssignsOnlyPublishedOpenTasks() {
        Long id = users.createUser(request("20260001", Set.of(10L)));

        assertEquals(Set.of(40L, 41L), assigned(id));
        assertEquals(1L, liveCount("edu_class_student", "student_id=" + id + " AND class_id=10"));
        assertEquals(Set.of(10L), profile(id).getClassIds());
        assertEquals(2026, profile(id).getGradeYear());
        assertEquals(1L, liveCount("system_users", "id=" + id));
        verify(notifications).sendToUsers(List.of(id), EduBusinessNotifyService.CLASS_JOINED,
                Map.of("className", "原班"), "/student/course");
        verify(rules, atLeastOnce()).getDataPermissionRule(EduHomeworkMapper.class.getName() + ".selectList");
        verify(rules, atLeastOnce()).getDataPermissionRule(EduClassMapper.class.getName() + ".selectById");
    }

    @Test
    void transferRemovesOpenAssignmentsButKeepsRecordedExpiredAndArchivedAssignments() {
        Long id = users.createUser(request("20260001", Set.of(10L)));
        sql.update("INSERT INTO edu_homework_record (homework_id,student_id,status,deleted) VALUES (40,?,0,0)", id);
        for (long taskId : List.of(42L, 43L)) {
            sql.update("INSERT INTO edu_homework_assignee (homework_id,student_id,class_id,deleted) VALUES (?, ?, 10, 0)", taskId, id);
        }
        clearInvocations(notifications);
        UserSaveReqVO edit = request("20260001", Set.of(20L));
        edit.setId(id); edit.getProfile().setGradeYear(2025);

        users.updateUser(edit);

        assertEquals(Set.of(40L, 42L, 43L, 50L), assigned(id));
        assertEquals(Set.of(20L), profile(id).getClassIds());
        assertEquals(2025, profile(id).getGradeYear());
        assertEquals(0L, liveCount("edu_class_student", "student_id=" + id + " AND class_id=10"));
        assertEquals(1L, liveCount("edu_class_student", "student_id=" + id + " AND class_id=20"));
        verify(notifications).sendToUsers(List.of(id), EduBusinessNotifyService.CLASS_JOINED,
                Map.of("className", "新班"), "/student/course");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void joiningRestoresAssignmentTombstoneAndRepeatedSaveDoesNotNotifyOrDuplicate() {
        Long id = users.createUser(request("20260001", Set.of()));
        sql.update("INSERT INTO edu_homework_assignee (homework_id,student_id,class_id,deleted) VALUES (40,?,10,1)", id);
        Long assignmentId = sql.queryForObject("SELECT id FROM edu_homework_assignee WHERE student_id=?", Long.class, id);
        UserSaveReqVO edit = request("20260001", Set.of(10L)); edit.setId(id);
        clearInvocations(notifications);

        users.updateUser(edit);
        users.updateUser(edit);

        assertEquals(Set.of(40L, 41L), assigned(id));
        assertEquals(assignmentId, sql.queryForObject("SELECT id FROM edu_homework_assignee WHERE student_id=? AND homework_id=40",
                Long.class, id));
        assertEquals(2L, sql.queryForObject("SELECT COUNT(*) FROM edu_homework_assignee WHERE student_id=?", Long.class, id));
        verify(notifications).sendToUsers(List.of(id), EduBusinessNotifyService.CLASS_JOINED,
                Map.of("className", "原班"), "/student/course");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void changingStudentTypeAlsoWithdrawsOpenAssignments() {
        Long id = users.createUser(request("20260001", Set.of(10L)));
        UserSaveReqVO edit = request("20260001", Set.of()); edit.setId(id); edit.setUserType(1);
        clearInvocations(notifications);

        users.updateUser(edit);

        assertTrue(assigned(id).isEmpty());
        assertEquals(0L, liveCount("edu_class_student", "student_id=" + id));
        assertTrue(profile(id).getClassIds().isEmpty());
        assertEquals(1, profile(id).getUserType());
        verifyNoInteractions(notifications);
    }

    @Test
    void snapshotSqlFailureRollsBackNewUserProfileAndClassMembership() {
        sql.execute("ALTER TABLE edu_homework_assignee ADD CONSTRAINT reject_new_assignment CHECK (homework_id <> 40)");

        assertThrows(RuntimeException.class, () -> users.createUser(request("20260001", Set.of(10L))));

        for (String table : List.of("system_users", "system_user_profile", "edu_class_student", "edu_homework_assignee")) {
            assertEquals(0L, liveCount(table, "1=1"), table);
        }
        verifyNoInteractions(notifications);
    }

    @Test
    void failedTransferRollsBackOldAssignmentWithdrawalAndProfileChange() {
        Long id = users.createUser(request("20260001", Set.of(10L)));
        sql.execute("ALTER TABLE edu_homework_assignee ADD CONSTRAINT reject_target_assignment CHECK (homework_id <> 50)");
        UserSaveReqVO edit = request("20260001", Set.of(20L)); edit.setId(id);
        clearInvocations(notifications);

        assertThrows(RuntimeException.class, () -> users.updateUser(edit));

        assertEquals(Set.of(40L, 41L), assigned(id));
        assertEquals(Set.of(10L), profile(id).getClassIds());
        assertEquals(1L, liveCount("edu_class_student", "student_id=" + id + " AND class_id=10"));
        assertEquals(0L, liveCount("edu_class_student", "student_id=" + id + " AND class_id=20"));
        verifyNoInteractions(notifications);
    }

    private <T> void registerMapper(Class<T> type, SqlSessionTemplate template) {
        context.registerBean(type, () -> template.getMapper(type));
    }

    private <T> T registerMock(Class<T> type) {
        T mock = mock(type);
        context.getBeanFactory().registerSingleton(type.getName(), mock);
        return mock;
    }

    private UserSaveReqVO request(String username, Set<Long> classes) {
        UserSaveReqVO request = new UserSaveReqVO();
        request.setUsername(username); request.setPassword("startPass1"); request.setUserType(2);
        request.setDeptId(10L); request.setNickname("测试学生");
        UserProfileExtReqVO profile = new UserProfileExtReqVO();
        profile.setGradeYear(2026); profile.setClassIds(classes); request.setProfile(profile);
        return request;
    }

    private void task(long id, long classId, int status, LocalDateTime deadline, boolean deleted) {
        sql.update("INSERT INTO edu_homework (id,class_id,status,deadline,deleted) VALUES (?,?,?,?,?)",
                id, classId, status, deadline, deleted);
    }

    private Set<Long> assigned(Long studentId) {
        return new HashSet<>(sql.queryForList("SELECT homework_id FROM edu_homework_assignee WHERE student_id=? AND deleted=0",
                Long.class, studentId));
    }

    private UserProfileDO profile(Long userId) {
        return context.getBean(UserProfileMapper.class).selectByUserId(userId);
    }

    private long liveCount(String table, String condition) {
        return sql.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE deleted=0 AND " + condition, Long.class);
    }

    private static String sqlType(TableFieldInfo field) {
        Class<?> type = field.getPropertyType();
        if (type == Boolean.class) return "BOOLEAN DEFAULT FALSE";
        if (type == Long.class) return "BIGINT";
        if (type == Integer.class) return "INT";
        if (type == Double.class) return "DOUBLE";
        if (type == LocalDateTime.class) return "TIMESTAMP";
        return "VARCHAR(10000)";
    }
}
