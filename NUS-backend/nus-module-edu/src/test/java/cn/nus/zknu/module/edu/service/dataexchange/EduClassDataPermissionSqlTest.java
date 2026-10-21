package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 用真实 MyBatis Plus 3.5.15 拦截器核实别名、子查询和原始 Mapper SQL。 */
class EduClassDataPermissionSqlTest extends BaseMockitoUnitTest {

    @Mock private PermissionCommonApi permissionApi;
    @Mock private DataPermissionRuleFactory ruleFactory;
    private DataPermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissionApi);
        rule.addDeptColumn("edu_class", "dept_id");
        rule.addDeptColumn("system_users", "dept_id");
        rule.addDeptColumn("system_dept", "id");
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        interceptor = new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void importLookupDerivedKeysKeepDepartmentPredicates() {
        login(false);
        com.baomidou.mybatisplus.core.MybatisConfiguration config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.addMapper(cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper.class);
        config.addMapper(cn.nus.zknu.module.system.dal.mysql.dept.DeptMapper.class);
        for (String method : List.of("Usernames", "Mobiles", "Emails")) {
            String id = cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper.class.getName() + ".selectImportBy" + method;
            String sql = config.getMappedStatement(id).getBoundSql(Map.of("keys", List.of("first", "second"))).getSql();
            assertTrue(rewrite(sql).contains("u.dept_id IN (10)"), sql);
        }
        String id = cn.nus.zknu.module.system.dal.mysql.dept.DeptMapper.class.getName() + ".selectImportByCodes";
        String sql = config.getMappedStatement(id).getBoundSql(Map.of("keys", Map.of("cs", "CS"))).getSql();
        assertTrue(rewrite(sql).contains("u.id IN (10)"), sql);
    }

    @Test
    void aliasAndInnerJoinGetDepartmentPredicate() {
        login(false);
        String sql = rewrite("SELECT ec.id FROM edu_class_course cc JOIN edu_class ec ON ec.id = cc.class_id WHERE cc.deleted = 0");
        assertTrue(sql.contains("ec.dept_id IN (10)"), sql);
    }

    @Test
    void leftJoinKeepsPredicateInOnClause() {
        login(false);
        String sql = rewrite("SELECT cc.id FROM edu_class_course cc LEFT JOIN edu_class ec ON ec.id = cc.class_id WHERE cc.deleted = 0");
        assertTrue(sql.contains("ON ec.id = cc.class_id AND ec.dept_id IN (10)"), sql);
        assertFalse(sql.substring(sql.indexOf("WHERE")).contains("ec.dept_id"), sql);
    }

    @Test
    void whereExistsInAndDerivedCountSubqueriesGetDepartmentPredicate() {
        login(false);
        for (String sql : List.of(
                "SELECT h.id FROM edu_homework h WHERE EXISTS (SELECT 1 FROM edu_class c WHERE c.id = h.class_id)",
                "SELECT id FROM edu_homework WHERE class_id IN (SELECT id FROM edu_class WHERE deleted = 0)",
                "SELECT COUNT(*) FROM (SELECT c.id FROM edu_class c WHERE c.deleted = 0) page_count")) {
            assertTrue(rewrite(sql).contains("dept_id IN (10)"), sql);
        }
    }

    @Test
    void joinOnNestedSubqueryIsNotTraversedByCurrentInterceptor() {
        login(false);
        String sql = rewrite("SELECT h.id FROM edu_homework_record hr JOIN edu_homework h ON h.id = hr.homework_id AND EXISTS (SELECT 1 FROM edu_class c WHERE c.id = h.class_id)");
        assertFalse(sql.contains("dept_id IN"), sql);
        verifyNoInteractions(permissionApi);
    }

    @Test
    void allScopeAndBackgroundJobsKeepEveryTargetMapperQueryAndCountUnchanged() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : List.of("mapper/statistics/StatisticsMapper.xml",
                "mapper/student/StudentCourseMapper.xml", "mapper/student/StudentHomeworkMapper.xml")) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("classIds", List.of(1L));
        params.put("studentId", 1L);
        params.put("courseId", 1L);
        params.put("classId", 1L);
        params.put("teacherId", 1L);
        params.put("academicYear", 2026);
        params.put("status", 0);
        for (boolean history : List.of(false, true)) {
            params.put("history", history);
            params.put("includeArchived", history);
            for (String id : configuration.getMappedStatementNames().stream().filter(name -> name.contains(".")).toList()) {
                String sql = configuration.getMappedStatement(id).getBoundSql(params).getSql();
                for (boolean loggedIn : List.of(false, true)) {
                    if (loggedIn) login(true);
                    else SecurityContextHolder.clearContext();
                    assertEquals(CCJSqlParserUtil.parse(sql).toString(), interceptor.parserSingle(sql, id), id);
                    String count = "SELECT COUNT(*) FROM (" + sql + ") page_count";
                    assertEquals(CCJSqlParserUtil.parse(count).toString(), interceptor.parserSingle(count, id), id + " count");
                }
            }
        }
    }

    private void login(boolean all) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2), new org.springframework.mock.web.MockHttpServletRequest());
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setAll(all);
        permission.setDeptIds(Set.of(10L));
        lenient().when(permissionApi.getDeptDataPermission(1L)).thenReturn(permission);
    }

    private String rewrite(String sql) {
        return interceptor.parserSingle(sql, "test.mapper.query");
    }
}
