package cn.nus.zknu.module.edu.dal.mysql.student;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentHomeworkMapperXmlContractTest {

    @Test
    void homeworkPageReturnsRedoDeadline() throws IOException {
        String mapper = new String(getClass().getResourceAsStream(
                "/mapper/student/StudentHomeworkMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        int start = mapper.indexOf("<select id=\"selectHomeworkPage\"");
        String statement = mapper.substring(start, mapper.indexOf("</select>", start));

        assertTrue(statement.contains("hr.redo_deadline AS redoDeadline"));
    }
}
