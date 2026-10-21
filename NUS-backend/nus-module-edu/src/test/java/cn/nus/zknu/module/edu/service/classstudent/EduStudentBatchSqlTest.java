package cn.nus.zknu.module.edu.service.classstudent;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentBatchMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRemindBatchMapper;
import cn.nus.zknu.module.system.dal.dataobject.notify.*;
import cn.nus.zknu.module.system.dal.dataobject.user.*;
import cn.nus.zknu.module.system.dal.mysql.notify.NotifyMessageMapper;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H2 MySQL 模式走真实数据权限与分页拦截器，逐条执行本分片的新 XML SQL。 */
class EduStudentBatchSqlTest extends BaseMockitoUnitTest {
    @Mock private PermissionCommonApi permissionApi;
    @Mock private DataPermissionRuleFactory ruleFactory;
    private SqlSessionFactory factory;
    private UnpooledDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        String audit = ", creator VARCHAR(30), updater VARCHAR(30), create_time TIMESTAMP DEFAULT NOW(), update_time TIMESTAMP DEFAULT NOW(), deleted BOOLEAN DEFAULT FALSE";
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE edu_class_student (id BIGINT AUTO_INCREMENT PRIMARY KEY, class_id BIGINT, student_id BIGINT, status INT, join_time TIMESTAMP, remark VARCHAR(100)" + audit + ")");
            sql.execute("CREATE TABLE edu_homework_assignee (id BIGINT AUTO_INCREMENT PRIMARY KEY, homework_id BIGINT, student_id BIGINT, class_id BIGINT, assigned_time TIMESTAMP" + audit + ")");
            sql.execute("CREATE TABLE system_user_profile (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, user_type INT, grade_year INT, professional_title INT, class_ids VARCHAR(300)" + audit + ")");
            sql.execute("CREATE TABLE edu_homework_remind_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, homework_id BIGINT, student_id BIGINT, remind_type VARCHAR(20), deadline TIMESTAMP, UNIQUE (homework_id, student_id, remind_type)" + audit + ")");
            sql.execute("CREATE TABLE system_notify_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, user_type INT, template_id BIGINT, template_code VARCHAR(100), template_type INT, template_nickname VARCHAR(100), template_content VARCHAR(1000), template_params VARCHAR(1000), read_status BOOLEAN, read_time TIMESTAMP" + audit + ")");
            sql.execute("CREATE TABLE system_users (id BIGINT PRIMARY KEY, username VARCHAR(30), password VARCHAR(100), password_change_required BOOLEAN, user_type INT, nickname VARCHAR(30), remark VARCHAR(100), dept_id BIGINT, post_ids VARCHAR(300), email VARCHAR(100), mobile VARCHAR(20), sex INT, avatar VARCHAR(300), status INT, login_ip VARCHAR(100), login_date TIMESTAMP" + audit + ")");
            sql.execute("INSERT INTO system_users (id,username,nickname,mobile,dept_id,user_type,status,deleted) VALUES (1,'AB123CD','姓名','13800000000',10,2,0,0),(2,'other','包含123姓名','13800000001',10,2,0,0),(3,'foreign123','外院','13800000002',20,2,0,0),(4,'deleted123','删除','13800000003',10,2,0,1),(5,'wronggrade123','其他年级','13800000004',10,2,0,0),(6,'profiledeleted123','删除画像','13800000005',10,2,0,0)");
            sql.execute("INSERT INTO system_user_profile (user_id,user_type,grade_year,deleted) VALUES (1,2,2026,0),(2,2,2026,0),(3,2,2026,0),(4,2,2026,0),(5,2,2025,0),(6,2,2026,1)");
        }
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("system_users", "dept_id");
        rule.addDeptColumn("system_dept", "id");
        rule.addDeptColumn("edu_class", "dept_id");
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        MybatisPlusInterceptor chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(chain);
        config.addMapper(AdminUserMapper.class);
        for (String xml : List.of("mapper/classstudent/EduClassStudentBatchMapper.xml",
                "mapper/homework/EduHomeworkRemindBatchMapper.xml", "mapper/notify/NotifyMessageMapper.xml")) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(xml)) {
                assertNotNull(input, xml);
                new XMLMapperBuilder(input, config, xml, config.getSqlFragments()).parse();
            }
        }
        factory = new MybatisSqlSessionFactoryBuilder().build(config);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setUserType(2), new MockHttpServletRequest());
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setAll(false); permission.setDeptIds(Set.of(10L));
        lenient().when(permissionApi.getDeptDataPermission(7L)).thenReturn(permission);
    }

    @AfterEach
    void clearLogin() { SecurityContextHolder.clearContext(); }

    @Test
    void classRelationInsertAndRestorePreserveRemarkStatusAndJoinTime() {
        try (SqlSession session = factory.openSession(true)) {
            EduClassStudentBatchMapper mapper = session.getMapper(EduClassStudentBatchMapper.class);
            EduClassStudentDO first = relation(1L); EduClassStudentDO second = relation(2L);
            assertEquals(2, mapper.insertRelations(List.of(first, second), "7"));
            List<Map<String, Object>> rows = rows("SELECT * FROM edu_class_student ORDER BY id");
            assertEquals("7", rows.get(0).get("CREATOR"));
            long id = ((Number) rows.get(0).get("ID")).longValue();
            execute("UPDATE edu_class_student SET deleted = 1 WHERE id = " + id);
            LocalDateTime joined = LocalDateTime.of(2026, 10, 10, 11, 0);
            assertEquals(1, mapper.restoreRelations(List.of(id), 0, joined, "恢复备注"));
            assertEquals(0, mapper.restoreRelations(List.of(id), 0, joined, "不可覆盖"));
            rows = rows("SELECT * FROM edu_class_student ORDER BY id");
            assertEquals(false, rows.get(0).get("DELETED"));
            assertEquals("恢复备注", rows.get(0).get("REMARK"));
            assertEquals(joined, ((Timestamp) rows.get(0).get("JOIN_TIME")).toLocalDateTime());
        }
    }

    @Test
    void assigneeLookupIncludesTombstonesAndBatchChangesRespectDeletionState() {
        try (SqlSession session = factory.openSession(true)) {
            EduClassStudentBatchMapper mapper = session.getMapper(EduClassStudentBatchMapper.class);
            assertEquals(2, mapper.insertAssignees(List.of(assignee(1L), assignee(2L)), "7"));
            List<EduHomeworkAssigneeDO> assigned = mapper.selectAssigneesIncludingDeleted(List.of(40L), List.of(1L, 2L));
            assertEquals(2, assigned.size());
            List<Long> ids = assigned.stream().map(EduHomeworkAssigneeDO::getId).toList();
            assertEquals(2, mapper.removeAssignees(ids));
            assertEquals(0, mapper.removeAssignees(ids));
            session.clearCache();
            assertTrue(mapper.selectAssigneesIncludingDeleted(List.of(40L), List.of(1L, 2L)).stream().allMatch(EduHomeworkAssigneeDO::getDeleted));
            assertEquals(2, mapper.restoreAssignees(ids));
            assertEquals(0, mapper.restoreAssignees(ids));
            session.clearCache();
            assertTrue(mapper.selectAssigneesIncludingDeleted(List.of(40L), List.of(1L, 2L)).stream().noneMatch(EduHomeworkAssigneeDO::getDeleted));
            assertTrue(mapper.selectAssigneesIncludingDeleted(List.of(99L), List.of(1L, 2L)).isEmpty());
        }
    }

    @Test
    void profileInsertAndCaseUpdateKeepExistingGradeAndSkipDeletedRows() {
        try (SqlSession session = factory.openSession(true)) {
            EduClassStudentBatchMapper mapper = session.getMapper(EduClassStudentBatchMapper.class);
            UserProfileDO first = profile(100L, Set.of(10L, 11L)); UserProfileDO second = profile(101L, Set.of(10L));
            assertEquals(2, mapper.insertProfiles(List.of(first, second), "7"));
            List<Map<String, Object>> rows = rows("SELECT * FROM system_user_profile WHERE user_id >= 100 ORDER BY user_id");
            first.setId(((Number) rows.get(0).get("ID")).longValue()); first.setClassIds(Set.of());
            second.setId(((Number) rows.get(1).get("ID")).longValue()); second.setClassIds(Set.of(12L));
            execute("UPDATE system_user_profile SET deleted = 1 WHERE user_id = 101");
            assertEquals(1, mapper.updateProfiles(List.of(first, second), "8"));
            rows = rows("SELECT * FROM system_user_profile WHERE user_id >= 100 ORDER BY user_id");
            assertEquals("[]", rows.get(0).get("CLASS_IDS"));
            assertEquals(2026, rows.get(0).get("GRADE_YEAR"));
            assertEquals("8", rows.get(0).get("UPDATER"));
            assertEquals("[10]", rows.get(1).get("CLASS_IDS"));
        }
    }

    @Test
    void reminderBatchReturnsGeneratedKeysAndConflictingInsertIsAtomic() {
        try (SqlSession session = factory.openSession(true)) {
            EduHomeworkRemindBatchMapper mapper = session.getMapper(EduHomeworkRemindBatchMapper.class);
            List<EduHomeworkRemindLogDO> logs = List.of(remind(1L), remind(2L));
            assertEquals(2, mapper.insertLogs(logs, null));
            assertNotNull(logs.get(0).getId()); assertNotNull(logs.get(1).getId());
            assertNotEquals(logs.get(0).getId(), logs.get(1).getId());
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> mapper.insertLogs(List.of(remind(3L), remind(1L)), null));
            assertEquals(2, rows("SELECT * FROM edu_homework_remind_log").size());
            assertEquals(1, mapper.deleteFailedLogs(List.of(logs.get(1).getId())));
            assertEquals(1L, ((Number) rows("SELECT * FROM edu_homework_remind_log").get(0).get("STUDENT_ID")).longValue());
        }
    }

    @Test
    void notifyMultiValueInsertPreservesJsonUnreadAndAuditAndIsAtomicOnFailure() {
        try (SqlSession session = factory.openSession(true)) {
            NotifyMessageMapper mapper = session.getMapper(NotifyMessageMapper.class);
            assertTrue(mapper.insertMessagesBatch(List.of(message(1L), message(2L))));
            NotifyMessageDO row = mapper.selectList(new LambdaQueryWrapperX<NotifyMessageDO>().eq(NotifyMessageDO::getUserId, 1L)).get(0);
            assertEquals(Map.of("targetUrl", "/student", "name", "学生"), row.getTemplateParams());
            assertEquals("正文", row.getTemplateContent()); assertFalse(row.getReadStatus());
            assertEquals(2, row.getUserType()); assertEquals("7", row.getCreator());
            assertNotNull(row.getCreateTime()); assertNotNull(row.getUpdateTime()); assertFalse(row.getDeleted());
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> mapper.insertMessages(List.of(message(3L), message(null)), null));
            assertEquals(2, rows("SELECT * FROM system_notify_message").size());
        }
    }

    @Test
    void scheduledWritesPreserveEmptyAuditDefaultsWithoutLogin() {
        SecurityContextHolder.clearContext();
        try (SqlSession session = factory.openSession(true)) {
            var mapper = session.getMapper(EduClassStudentBatchMapper.class);
            assertEquals(1, mapper.insertRelations(List.of(relation(100L)), null));
            assertEquals(1, mapper.insertAssignees(List.of(assignee(100L)), null));
            var profile = profile(100L, Set.of(10L));
            assertEquals(1, mapper.insertProfiles(List.of(profile), null));
            var inserted = rows("SELECT * FROM system_user_profile WHERE user_id = 100").get(0);
            profile.setId(((Number) inserted.get("ID")).longValue());
            assertEquals(1, mapper.updateProfiles(List.of(profile), null));
            assertEquals(1, session.getMapper(EduHomeworkRemindBatchMapper.class).insertLogs(List.of(remind(100L)), null));
            assertTrue(session.getMapper(NotifyMessageMapper.class).insertMessagesBatch(List.of(message(100L))));
            for (String table : List.of("edu_class_student", "edu_homework_assignee", "system_user_profile",
                    "edu_homework_remind_log", "system_notify_message")) {
                var result = rows("SELECT creator, updater FROM " + table + " WHERE "
                        + (table.equals("system_user_profile") || table.equals("system_notify_message") ? "user_id" : "student_id") + " = 100");
                assertEquals(1, result.size(), table);
                assertEquals("", result.get(0).get("CREATOR"), table);
                assertEquals("", result.get(0).get("UPDATER"), table);
            }
        }
    }

    @Test
    void gradeExistsPageKeepsContainsSearchDescendingOrderCountAndDepartmentPermission() {
        try (SqlSession session = factory.openSession()) {
            EduClassStudentPageReqVO req = new EduClassStudentPageReqVO(); req.setPageSize(1);
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUserDO> query = new LambdaQueryWrapperX<AdminUserDO>()
                    .eq(AdminUserDO::getUserType, 2).eq(AdminUserDO::getStatus, 0)
                    .apply("EXISTS (SELECT 1 FROM system_user_profile p WHERE p.user_id = system_users.id AND p.user_type = {0} AND p.grade_year = {1} AND p.deleted = 0)", 2, 2026)
                    .and(q -> q.like(AdminUserDO::getUsername, "123").or().like(AdminUserDO::getNickname, "123").or().like(AdminUserDO::getMobile, "123"))
                    .orderByDesc(AdminUserDO::getId);
            var page = session.getMapper(AdminUserMapper.class).selectPage(req, query);
            assertEquals(2L, page.getTotal()); assertEquals(List.of(2L), page.getList().stream().map(AdminUserDO::getId).toList());
            verify(permissionApi, atLeastOnce()).getDeptDataPermission(7L);
        }
    }

    private EduClassStudentDO relation(Long studentId) {
        EduClassStudentDO row = new EduClassStudentDO(); row.setClassId(10L); row.setStudentId(studentId);
        row.setStatus(0); row.setJoinTime(LocalDateTime.of(2026, 10, 10, 10, 0)); row.setRemark("备注"); return row;
    }
    private EduHomeworkAssigneeDO assignee(Long studentId) {
        EduHomeworkAssigneeDO row = new EduHomeworkAssigneeDO(); row.setHomeworkId(40L); row.setClassId(10L);
        row.setStudentId(studentId); row.setAssignedTime(LocalDateTime.of(2026, 10, 10, 10, 0)); return row;
    }
    private UserProfileDO profile(Long userId, Set<Long> classIds) {
        UserProfileDO row = new UserProfileDO(); row.setUserId(userId); row.setUserType(2); row.setGradeYear(2026); row.setClassIds(classIds); return row;
    }
    private EduHomeworkRemindLogDO remind(Long studentId) {
        EduHomeworkRemindLogDO row = new EduHomeworkRemindLogDO(); row.setHomeworkId(40L); row.setStudentId(studentId);
        row.setRemindType("AUTO"); row.setDeadline(LocalDateTime.of(2026, 10, 10, 11, 0)); return row;
    }
    private NotifyMessageDO message(Long userId) {
        return new NotifyMessageDO().setUserId(userId).setUserType(2).setTemplateId(8L).setTemplateCode("test")
                .setTemplateType(1).setTemplateNickname("教务").setTemplateContent("正文")
                .setTemplateParams(Map.of("targetUrl", "/student", "name", "学生")).setReadStatus(false);
    }
    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) { throw new AssertionError(ex); }
    }
    private List<Map<String, Object>> rows(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (result.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                rows.add(row);
            }
            return rows;
        } catch (SQLException ex) { throw new AssertionError(ex); }
    }
}
