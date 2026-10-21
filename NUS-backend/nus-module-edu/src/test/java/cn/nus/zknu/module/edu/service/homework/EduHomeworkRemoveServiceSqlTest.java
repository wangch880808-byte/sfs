package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.*;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.aireview.*;
import cn.nus.zknu.module.edu.dal.mysql.homework.*;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.*;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_REMOVE_IDS_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H2 MySQL 模式走真实 MyBatis 权限、分页链与 Spring 事务，验证逻辑删除与回滚。 */
class EduHomeworkRemoveServiceSqlTest {
    private EduHomeworkRemoveService service;
    private EduHomeworkRemoveService target;
    private EduDataScopeService scope;
    private DataPermissionRuleFactory rules;
    private JdbcTemplate sql;

    private static final Map<Class<?>, Class<?>> TABLES = Map.ofEntries(
            Map.entry(EduHomeworkMapper.class, EduHomeworkDO.class),
            Map.entry(EduHomeworkAssigneeMapper.class, EduHomeworkAssigneeDO.class),
            Map.entry(EduHomeworkRecordMapper.class, EduHomeworkRecordDO.class),
            Map.entry(EduHomeworkRecordHistoryMapper.class, EduHomeworkRecordHistoryDO.class),
            Map.entry(EduHomeworkGradingActionMapper.class, EduHomeworkGradingActionDO.class),
            Map.entry(EduHomeworkExtensionMapper.class, EduHomeworkExtensionDO.class),
            Map.entry(EduHomeworkRemindLogMapper.class, EduHomeworkRemindLogDO.class),
            Map.entry(EduHomeworkNotifyStateMapper.class, EduHomeworkNotifyStateDO.class),
            Map.entry(EduHomeworkAiReviewMapper.class, EduHomeworkAiReviewDO.class),
            Map.entry(AiEduReviewTaskMapper.class, AiEduReviewTaskDO.class),
            Map.entry(AiEduFeedbackMapper.class, AiEduFeedbackDO.class));

    @BeforeEach void setUp() {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), ds));
        rules = mock(DataPermissionRuleFactory.class);
        var rule = new DeptDataPermissionRule(mock(PermissionCommonApi.class));
        rule.addDeptColumn("system_users", "dept_id"); rule.addDeptColumn("system_dept", "id");
        rule.addDeptColumn("edu_class", "dept_id");
        when(rules.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(rules)));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(plugins);
        TABLES.keySet().forEach(config::addMapper);
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        sql = new JdbcTemplate(ds);
        for (Class<?> type : TABLES.values()) {
            var info = TableInfoHelper.getTableInfo(type);
            String fields = info.getFieldList().stream().map(field -> field.getColumn() + " " + sqlType(field))
                    .collect(java.util.stream.Collectors.joining(","));
            sql.execute("CREATE TABLE " + info.getTableName() + " (id BIGINT PRIMARY KEY," + fields + ")");
        }
        var template = new SqlSessionTemplate(factory);
        target = new EduHomeworkRemoveService();
        for (var field : EduHomeworkRemoveService.class.getDeclaredFields()) {
            if (TABLES.containsKey(field.getType())) {
                ReflectionTestUtils.setField(target, field.getName(), template.getMapper(field.getType()));
            }
        }
        scope = mock(EduDataScopeService.class);
        ReflectionTestUtils.setField(target, "dataScopeService", scope);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
                new AnnotationTransactionAttributeSource()));
        service = (EduHomeworkRemoveService) proxy.getProxy();
        login(7L);
        sql.update("INSERT INTO edu_homework (id,teacher_id,status,deleted) VALUES (10,7,3,0),(20,7,0,0),(30,8,1,0),(40,7,1,1)");
        sql.update("INSERT INTO edu_homework_record (id,homework_id,student_id,status,deleted) VALUES "
                + "(1,10,101,1,0),(2,10,102,2,0),(3,10,103,3,0),(4,10,104,4,0),(5,10,105,0,0),"
                + "(6,10,106,1,1),(7,30,107,1,0)");
        sql.update("INSERT INTO edu_homework_assignee (id,homework_id,student_id,deleted) VALUES "
                + "(1,10,101,0),(2,10,102,0),(3,10,103,0),(4,10,104,0),(5,10,105,0),(6,10,106,1),(7,30,107,0)");
        for (Class<?> type : TABLES.values()) {
            if (type == EduHomeworkDO.class || type == EduHomeworkRecordDO.class || type == EduHomeworkAssigneeDO.class) continue;
            var info = TableInfoHelper.getTableInfo(type);
            String column = type == AiEduFeedbackDO.class ? "homework_record_id" : "homework_id";
            long selected = type == AiEduFeedbackDO.class ? 1 : 10;
            long other = type == AiEduFeedbackDO.class ? 7 : 30;
            sql.update("INSERT INTO " + info.getTableName() + " (id," + column + ",deleted) VALUES (1,?,0),(2,?,0),(3,?,1)", selected, other, selected);
        }
    }

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void impactCountsSubmissionsAndAssignmentsAndDeduplicatesTasks() {
        sql.update("INSERT INTO edu_homework (id,teacher_id,status,deleted) VALUES (50,7,1,0)");
        sql.update("INSERT INTO edu_homework_assignee (id,homework_id,student_id,deleted) VALUES (8,50,101,0),(9,50,108,0)");
        var impact = service.getRemoveImpact(List.of(10L, 20L, 50L, 10L));
        assertEquals(3, impact.getHomeworkCount()); assertEquals(4, impact.getSubmittedCount());
        assertEquals(6, impact.getStudentCount());
        assertEquals(2, liveCount("edu_homework", "id IN (10,20)"));
    }

    @Test void authorCanRemoveArchivedAndDraftWithAllDependents() {
        service.removeHomeworks(List.of(10L, 20L));
        assertEquals(0, liveCount("edu_homework", "id IN (10,20)"));
        for (Class<?> type : TABLES.values()) {
            if (type == EduHomeworkDO.class) continue;
            var info = TableInfoHelper.getTableInfo(type);
            String column = type == AiEduFeedbackDO.class ? "homework_record_id" : "homework_id";
            long selected = type == AiEduFeedbackDO.class ? 1 : 10;
            assertEquals(0, liveCount(info.getTableName(), column + "=" + selected), info.getTableName());
            assertTrue(sql.queryForObject("SELECT COUNT(*) FROM " + info.getTableName(), Long.class) > 0,
                    "必须保留物理记录：" + info.getTableName());
            long other = type == AiEduFeedbackDO.class ? 7 : 30;
            assertEquals(1, liveCount(info.getTableName(), column + "=" + other), "其他任务：" + info.getTableName());
        }
        verify(rules, atLeastOnce()).getDataPermissionRule(AiEduFeedbackMapper.class.getName() + ".delete");
        assertServiceException(() -> service.getRemoveImpact(List.of(10L)), FORBIDDEN);
    }

    @Test void administratorMayRemoveAnotherAuthorsTask() {
        login(99L); when(scope.isGlobalAdmin()).thenReturn(true);
        assertEquals(1, service.getRemoveImpact(List.of(30L)).getHomeworkCount());
        service.removeHomeworks(List.of(30L));
        assertEquals(0, liveCount("edu_homework", "id=30"));
        assertEquals(2, liveCount("edu_homework", "id IN (10,20)"));
    }

    @Test void anotherTeacherCannotReadImpactOrRemove() {
        login(8L);
        assertServiceException(() -> service.getRemoveImpact(List.of(10L)), FORBIDDEN);
        assertServiceException(() -> service.removeHomeworks(List.of(10L)), FORBIDDEN);
        assertEquals(1, liveCount("edu_homework", "id=10"));
    }

    @Test void oneUnauthorizedIdFailsTheWholeBatchBeforeWrites() {
        assertServiceException(() -> service.removeHomeworks(List.of(10L, 30L)), FORBIDDEN);
        assertEquals(2, liveCount("edu_homework", "id IN (10,30)"));
        assertEquals(5, liveCount("edu_homework_record", "homework_id=10"));
        assertEquals(1, liveCount("ai_edu_feedback", "id=1"));
    }

    @Test void dependentFailureRollsBackPreviousLogicalDeletes() {
        var failure = mock(EduHomeworkNotifyStateMapper.class);
        doThrow(new IllegalStateException("模拟删除失败")).when(failure).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        ReflectionTestUtils.setField(target, "notifyStateMapper", failure);
        assertThrows(IllegalStateException.class, () -> service.removeHomeworks(List.of(10L)));
        assertEquals(1, liveCount("edu_homework", "id=10"));
        assertEquals(1, liveCount("ai_edu_feedback", "id=1"));
        assertEquals(1, liveCount("edu_homework_ai_review", "id=1"));
        assertEquals(1, liveCount("edu_homework_remind_log", "id=1"));
    }

    @Test void missingOrDeletedIdRejectsWholeBatch() {
        assertServiceException(() -> service.removeHomeworks(List.of(10L, 999L)), FORBIDDEN);
        assertServiceException(() -> service.getRemoveImpact(List.of(40L)), FORBIDDEN);
        assertEquals(1, liveCount("edu_homework", "id=10"));
    }

    @Test void invalidAndOversizedInputsCannotFallThroughToUnboundedDelete() {
        assertServiceException(() -> service.removeHomeworks(List.of()), HOMEWORK_REMOVE_IDS_INVALID);
        assertServiceException(() -> service.removeHomeworks(Collections.nCopies(101, 10L)), HOMEWORK_REMOVE_IDS_INVALID);
        assertServiceException(() -> service.removeHomeworks(Arrays.asList(10L, null)), HOMEWORK_REMOVE_IDS_INVALID);
        assertServiceException(() -> service.removeHomeworks(List.of(-1L)), HOMEWORK_REMOVE_IDS_INVALID);
        logout();
        assertServiceException(() -> service.removeHomeworks(List.of(10L)), FORBIDDEN);
        assertEquals(1, liveCount("edu_homework", "id=10"));
    }

    private void login(long userId) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(userId).setUserType(2), new MockHttpServletRequest());
    }
    private long liveCount(String table, String condition) {
        return sql.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE deleted=0 AND " + condition, Long.class);
    }
    private static String sqlType(TableFieldInfo field) {
        Class<?> type = field.getPropertyType();
        if (type == Boolean.class) return "BOOLEAN";
        if (type == Long.class) return "BIGINT";
        if (type == Integer.class) return "INT";
        if (type == Double.class) return "DOUBLE";
        if (type == java.time.LocalDateTime.class) return "TIMESTAMP";
        return "VARCHAR(10000)";
    }
}
