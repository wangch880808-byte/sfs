package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import cn.nus.zknu.module.system.dal.mysql.dept.DeptMapper;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 使用现有 H2 依赖执行真实 Mapper SQL，检查输入键、逻辑删除和教师隔离。 */
class EduImportBatchLookupSqlTest {
    private SqlSessionFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource ds = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE", "sa", "");
        try (Connection connection = ds.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE system_users (id BIGINT PRIMARY KEY, username VARCHAR(30), "
                    + "mobile VARCHAR(20), email VARCHAR(80), dept_id BIGINT, deleted BOOLEAN)");
            sql.execute("INSERT INTO system_users VALUES (1,'SELF','13800138000','taken@example.com',10,0),"
                    + "(2,'FIRST','13800138000','taken@example.com',10,0),"
                    + "(3,'SECOND','13800138000','taken@example.com',20,0),"
                    + "(4,'DELETED','13900139000','deleted@example.com',20,1)");
            sql.execute("CREATE TABLE system_dept (id BIGINT PRIMARY KEY, dept_code VARCHAR(30), deleted BOOLEAN)");
            sql.execute("INSERT INTO system_dept VALUES (10,'CS',0),(20,'FOREIGN',1)");
            sql.execute("CREATE TABLE edu_class_student (id BIGINT PRIMARY KEY, class_id BIGINT, student_id BIGINT, deleted BOOLEAN, remark VARCHAR(30))");
            sql.execute("INSERT INTO edu_class_student VALUES (1,100,1,0,'active'),(2,200,2,1,'restore'),(3,300,3,0,'unrelated')");
            sql.execute("CREATE TABLE edu_question_bank (id BIGINT PRIMARY KEY, teacher_id BIGINT, title VARCHAR(30), "
                    + "requirement VARCHAR(30), indicator_first_id BIGINT, indicator_second_id BIGINT, homework_level INT, "
                    + "creator VARCHAR(30), updater VARCHAR(30), create_time TIMESTAMP, update_time TIMESTAMP, deleted BOOLEAN)");
            sql.execute("INSERT INTO edu_question_bank (id,teacher_id,title,deleted) VALUES "
                    + "(1,7,'same',0),(2,8,'same',0),(3,NULL,'background',0),(4,7,'deleted',1)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        configuration.addMapper(AdminUserMapper.class);
        configuration.addMapper(DeptMapper.class);
        configuration.addMapper(EduClassStudentMapper.class);
        configuration.addMapper(EduQuestionBankMapper.class);
        factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void keyedLookupsKeepDatabaseCaseMatchingAndExcludeDeletedAccounts() {
        try (SqlSession session = factory.openSession()) {
            AdminUserMapper mapper = session.getMapper(AdminUserMapper.class);
            var users = mapper.selectImportByUsernames(List.of("self", "DELETED", "missing"));
            assertEquals(1, users.size());
            assertEquals("self", users.get(0).getLookupKey());
            assertEquals("SELF", users.get(0).getUsername());
            var mobileOwners = mapper.selectImportByMobiles(List.of("13800138000"));
            var emailOwners = mapper.selectImportByEmails(List.of("TAKEN@example.com"));
            assertEquals(3, mobileOwners.size());
            assertEquals(3, emailOwners.size());
            var data = new EduAccountPreviewData();
            data.setContactOwners(mobileOwners, emailOwners);
            var self = users.get(0);
            // 改动时排除本人，取查询返回的第一个其他账号；未改动忽略历史重复。
            assertNull(data.mobileOwner("13800138000", self));
            assertNull(data.emailOwner("TAKEN@example.com", self));
            self.setMobile("13700137000");
            self.setEmail("old@example.com");
            assertEquals("FIRST", data.mobileOwner("13800138000", self).getUsername());
            assertEquals("FIRST", data.emailOwner("TAKEN@example.com", self).getUsername());
            assertNull(data.mobileOwner("13900139000", self));
            var depts = session.getMapper(DeptMapper.class).selectImportByCodes(Map.of("cs", "CS", "FOREIGN", "FOREIGN"));
            assertEquals(1, depts.size());
            assertEquals("cs", depts.get(0).getLookupKey());
            assertEquals(10L, depts.get(0).getId());
        }
    }

    @Test
    void batchRelationsIncludeDeletedAndTeacherQueryKeepsNullSemantics() {
        try (SqlSession session = factory.openSession()) {
            var relations = session.getMapper(EduClassStudentMapper.class)
                    .selectImportRelationsIncludingDeleted(List.of(100L, 200L), List.of(1L, 2L));
            assertEquals(2, relations.size());
            assertEquals(List.of(false, true), relations.stream().map(item -> item.getDeleted()).toList());
            var mapper = session.getMapper(EduQuestionBankMapper.class);
            assertEquals(List.of(1L), mapper.selectImportByTeacherId(7L).stream().map(item -> item.getId()).toList());
            assertEquals(List.of(2L), mapper.selectImportByTeacherId(8L).stream().map(item -> item.getId()).toList());
            assertEquals(List.of(3L), mapper.selectImportByTeacherId(null).stream().map(item -> item.getId()).toList());
        }
    }
}
