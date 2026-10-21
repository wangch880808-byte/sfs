package cn.nus.zknu.module.edu.dal.mysql.statistics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsMapperSqlContractTest {

    @Test
    void scoreBandsUsePolicyRatiosAndSemesterName() throws IOException {
        String mapper = new String(getClass().getResourceAsStream(
                "/mapper/statistics/StatisticsMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        int start = mapper.indexOf("<select id=\"selectTotalPassRate\"");
        int end = mapper.indexOf("</select>", start);
        String statement = mapper.substring(start, end);
        assertTrue(statement.contains("GREATEST(h.pass_score, h.score * #{goodRatio})"));
        assertTrue(statement.contains("#{excellentRatio}"));
        assertTrue(statement.contains("h.semester_name"));
        assertTrue(!statement.contains("-08-01"));
    }

    @Test
    void teacherCourseOptionsKeepExclusiveTeamsAndIncludePublicClaims() throws IOException {
        String mapper = new String(getClass().getResourceAsStream(
                "/mapper/statistics/StatisticsMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        int start = mapper.indexOf("<select id=\"selectTeacherCourses\"");
        int end = mapper.indexOf("</select>", start);
        String statement = mapper.substring(start, end);

        assertTrue(statement.contains("cc.teacher_Id = #{teacherId}"));
        // 课程团队读关联表，不再匹配逗号串
        assertTrue(statement.contains("capacity_course_member team"));
        assertTrue(statement.contains("team.user_id = #{teacherId}"));
        assertTrue(statement.contains("cc.is_public_course != 0"));
        assertTrue(statement.contains("claim_binding.claim_teacher_id = #{teacherId}"));
        assertTrue(statement.contains("claim_binding.status = 0"));
        assertTrue(!statement.contains("teach_assistant_ids"));
    }

    @Test
    void unreadMessagesCountsUnreadRowsWithinTeacherAndAssistantScope() throws IOException {
        String mapper = new String(getClass().getResourceAsStream(
                "/mapper/statistics/StatisticsMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        int start = mapper.indexOf("<select id=\"selectUnreadMessages\"");
        int end = mapper.indexOf("</select>", start);
        String statement = mapper.substring(start, end);
        assertTrue(statement.contains("COUNT(*)"));
        assertTrue(statement.contains("m.read_status = 0"));
        assertTrue(statement.contains("c.teacher_Id = #{teacherId}"));
        assertTrue(statement.contains("capacity_course_member team"));
        assertTrue(statement.contains("edu_class_course_assistant assistant"));
        assertTrue(statement.contains("assistant.class_course_id = cc.id"));
        assertTrue(statement.contains("cc.claim_teacher_id = #{teacherId}"));
        assertTrue(statement.contains("cc.claim_teacher_id IS NULL"));
        assertTrue(statement.contains("c.is_public_course != 0"));
        assertTrue(!statement.contains("assistant_teacher_ids"));
        assertTrue(!statement.contains("FIND_IN_SET"));
    }
}
