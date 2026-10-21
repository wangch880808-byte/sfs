package cn.nus.zknu.module.edu.dal.mysql.student;

import cn.nus.zknu.module.edu.enums.homework.EduHomeworkGradingActionEnum;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等级闯关查询的口径约定（SQL 本身已在 MySQL 副本库上实测）。
 */
class StudentCourseMapperXmlContractTest {

    @Test
    void levelTasksIgnoreUndoneGrading() throws IOException {
        String statement = levelTasksStatement();

        // 撤销成功时操作行的通知状态记为“已撤销”，对应的 CORRECT 轨迹不能当作上一次批改的分数
        assertEquals(2, EduHomeworkGradingActionEnum.NOTIFY_UNDONE, "XML 里写死了 2，改常量时要同步改查询");
        assertTrue(statement.contains("hh.event_type IN ('CORRECT', 'GRADEBOOK_IMPORT')"));
        assertTrue(statement.contains("NOT EXISTS (SELECT 1 FROM edu_homework_grading_action ga"));
        assertTrue(statement.contains("ga.history_id = hh.id"));
        assertTrue(statement.contains("ga.notify_status = 2"));
    }

    @Test
    void levelTasksUseTheLatestFinalScoreIncludingGradebookImports() throws IOException {
        String statement = levelTasksStatement();
        int start = statement.indexOf("SELECT hh.score");
        String scoreQuery = statement.substring(start, statement.indexOf("AS lastCorrectScore", start));

        assertTrue(scoreQuery.contains("hh.event_type IN ('CORRECT', 'GRADEBOOK_IMPORT')"));
        assertTrue(scoreQuery.contains("ORDER BY hh.id DESC LIMIT 1"));
        assertFalse(scoreQuery.contains("'REDO'"));
        assertFalse(scoreQuery.contains("'RESUBMIT'"));
    }

    @Test
    void levelTasksReturnTheColumnsForTheStudentsEffectiveDeadline() throws IOException {
        String statement = levelTasksStatement();

        assertTrue(statement.contains("FROM edu_homework_extension ext"));
        assertTrue(statement.contains("ext.deleted = 0) AS personalDeadline"));
        assertTrue(statement.contains("hr.redo_deadline AS redoDeadline"));
    }

    private String levelTasksStatement() throws IOException {
        String mapper = new String(getClass().getResourceAsStream(
                "/mapper/student/StudentCourseMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        int start = mapper.indexOf("<select id=\"selectLevelTasks\"");
        return mapper.substring(start, mapper.indexOf("</select>", start));
    }
}
