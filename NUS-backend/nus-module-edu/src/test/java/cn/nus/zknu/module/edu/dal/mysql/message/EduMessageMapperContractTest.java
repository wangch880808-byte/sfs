package cn.nus.zknu.module.edu.dal.mysql.message;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EduMessageMapperContractTest {
    @Test
    void studentAccessRequiresActiveClassCourseBindingAndMembership() throws Exception {
        String sql = String.join(" ", EduMessageMapper.class
                .getMethod("existsStudentConversation", Long.class, Long.class, Long.class)
                .getAnnotation(Select.class).value());
        assertTrue(sql.contains("cs.student_id = #{studentId}"));
        assertTrue(sql.contains("cs.class_id = #{classId}"));
        assertTrue(sql.contains("cc.course_id = #{courseId}"));
        assertTrue(sql.contains("cl.status = 0"));
        assertTrue(sql.contains("cc.status = 0"));
        assertTrue(sql.contains("c.status = 0"));
    }
}
