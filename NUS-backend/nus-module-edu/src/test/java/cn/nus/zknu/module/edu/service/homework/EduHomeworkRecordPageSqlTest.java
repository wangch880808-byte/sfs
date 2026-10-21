package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.biz.system.permission.PermissionCommonApi;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordPageMapper;
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

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H2 MySQL 模式执行完整数据权限、分页拦截器链，避免仅解析 SQL 漏掉 COUNT 路径。 */
class EduHomeworkRecordPageSqlTest {
    private SqlSessionFactory factory;
    private DataPermissionRuleFactory rules;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = ds.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE edu_homework (id BIGINT PRIMARY KEY, teacher_id BIGINT, status INT, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework VALUES (10,7,1,0),(20,7,3,0),(30,8,1,0),(40,7,1,1)");
            sql.execute("CREATE TABLE edu_homework_record (id BIGINT PRIMARY KEY, homework_id BIGINT, student_id BIGINT,"
                    + " score INT, status INT, submit_time TIMESTAMP, content VARCHAR(100), deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework_record VALUES "
                    + "(1,10,101,90,2,'2026-01-01','answer',0),(2,10,102,90,2,'2026-01-01',NULL,0),"
                    + "(3,10,103,80,2,'2026-01-01',NULL,0),(4,10,104,NULL,2,'2026-01-01',NULL,0),"
                    + "(5,10,105,NULL,2,'2026-01-01',NULL,0),(6,10,106,100,3,'2026-01-01',NULL,0),"
                    + "(7,10,107,100,4,'2026-01-01',NULL,0),(8,10,108,NULL,1,'2026-01-01',NULL,0),"
                    + "(9,20,109,70,2,'2026-01-01',NULL,0),(10,30,110,60,2,'2026-01-01',NULL,0),"
                    + "(11,40,111,50,2,'2026-01-01',NULL,0),(12,10,112,100,2,'2026-01-01',NULL,1),"
                    + "(13,10,113,NULL,0,'2026-01-01',NULL,0)");
            sql.execute("CREATE TABLE edu_homework_ai_review (id BIGINT PRIMARY KEY, homework_record_id BIGINT,"
                    + " status INT, ai_score INT, ai_comment VARCHAR(100), ai_confidence DOUBLE, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_homework_ai_review VALUES (1,1,2,90,'old',0.9,0),"
                    + "(2,1,3,NULL,'failed',0.5,0),(3,1,2,100,'deleted',1,1),(4,2,2,80,'other',0.8,0)");
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        rules = mock(DataPermissionRuleFactory.class);
        var rule = new DeptDataPermissionRule(mock(PermissionCommonApi.class));
        rule.addDeptColumn("edu_class", "dept_id");
        rule.addDeptColumn("system_users", "dept_id");
        rule.addDeptColumn("system_dept", "id");
        when(rules.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(rules)));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(plugins);
        config.addMapper(EduHomeworkRecordPageMapper.class);
        config.addMapper(EduHomeworkAiReviewMapper.class);
        for (String resource : List.of("mapper/homework/EduHomeworkRecordPageMapper.xml",
                "mapper/aireview/EduHomeworkAiReviewMapper.xml")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        factory = new MybatisSqlSessionFactoryBuilder().build(config);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setUserType(2), new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void latestUsesMaxLiveIdEvenWhenLatestHasNoScore() {
        try (var session = factory.openSession()) {
            var reviews = session.getMapper(EduHomeworkAiReviewMapper.class).selectLatestByRecordIds(List.of(1L, 2L, 3L));
            assertEquals(2, reviews.size());
            var byRecord = reviews.stream().collect(Collectors.toMap(r -> r.getHomeworkRecordId(), r -> r));
            assertEquals(2L, byRecord.get(1L).getId());
            assertEquals(3, byRecord.get(1L).getStatus());
            assertNull(byRecord.get(1L).getAiScore());
            assertEquals("failed", byRecord.get(1L).getAiComment());
            assertEquals(0.5, byRecord.get(1L).getAiConfidence());
            assertEquals(80, byRecord.get(2L).getAiScore());
            verify(rules, atLeastOnce()).getDataPermissionRule(EduHomeworkAiReviewMapper.class.getName() + ".selectLatestByRecordIds");
        }
    }

    @Test
    void ranksKeepCompetitionTiesNullsAndOnlyCorrectedRecords() {
        try (var session = factory.openSession()) {
            var rows = session.getMapper(EduHomeworkRecordPageMapper.class).selectRanks(List.of(10L, 20L));
            assertEquals(6, rows.size());
            var ranks = rows.stream().collect(Collectors.toMap(r -> r.getStudentId(), r -> r.getRank()));
            assertEquals(Map.of(101L,1,102L,1,103L,3,104L,4,105L,4,109L,1), ranks);
            assertTrue(rows.stream().filter(r -> r.getHomeworkId() == 10L).allMatch(r -> r.getTotal() == 5));
            assertEquals(1, rows.stream().filter(r -> r.getHomeworkId() == 20L).findFirst().orElseThrow().getTotal());
            verify(rules, atLeastOnce()).getDataPermissionRule(EduHomeworkRecordPageMapper.class.getName() + ".selectRanks");
        }
    }

    @Test
    void ownedJoinPreservesFiltersOrderingCountPagesAndUnpagedMode() {
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduHomeworkRecordPageMapper.class);
            var req = new EduHomeworkRecordPageReqVO();
            req.setPageSize(3);
            var first = mapper.selectOwnedPage(req,7L,false);
            assertEquals(10, first.getTotal());
            assertEquals(List.of(13L,9L,8L), first.getList().stream().map(r -> r.getId()).toList());
            req.setPageNo(2);
            assertEquals(List.of(7L,6L,5L), mapper.selectOwnedPage(req,7L,false).getList().stream().map(r -> r.getId()).toList());
            req.setPageNo(1);
            req.setStudentId(101L);
            assertEquals("answer", mapper.selectOwnedPage(req,7L,false).getList().get(0).getContent());
            req.setStatus(3);
            assertEquals(0, mapper.selectOwnedPage(req,7L,false).getTotal());
            req.setStudentId(null);
            assertEquals(List.of(6L), mapper.selectOwnedPage(req,7L,false).getList().stream().map(r -> r.getId()).toList());
            req.setStatus(null);
            req.setPageSize(-1);
            assertEquals(10, mapper.selectOwnedPage(req,7L,false).getTotal());
            assertEquals(1, mapper.selectOwnedPage(req,8L,false).getTotal());
            assertEquals(0, mapper.selectOwnedPage(req,null,false).getTotal());
            verify(rules, atLeastOnce()).getDataPermissionRule(EduHomeworkRecordPageMapper.class.getName() + ".selectOwnedRecords");
        }
    }

    @Test
    void correctionJoinKeepsResubmitsDefaultsAndIgnoresStudentFilter() {
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(EduHomeworkRecordPageMapper.class);
            var req = new EduHomeworkRecordPageReqVO();
            req.setPageSize(-1);
            req.setStudentId(999L);
            assertEquals(List.of(8L,1L,2L,3L,4L,5L,9L,6L,7L),
                    mapper.selectOwnedPage(req,7L,true).getList().stream().map(r -> r.getId()).toList());
            req.setStatus(1);
            assertEquals(List.of(8L,7L), mapper.selectOwnedPage(req,7L,true).getList().stream().map(r -> r.getId()).toList());
            req.setStatus(3);
            assertEquals(List.of(6L), mapper.selectOwnedPage(req,7L,true).getList().stream().map(r -> r.getId()).toList());
            req.setStatus(null);
            req.setPageSize(2);
            assertEquals(9, mapper.selectOwnedPage(req,7L,true).getTotal());
        }
    }
}
