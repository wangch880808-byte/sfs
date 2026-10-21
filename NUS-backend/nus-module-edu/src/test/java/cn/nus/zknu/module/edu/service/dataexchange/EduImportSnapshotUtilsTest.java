package cn.nus.zknu.module.edu.service.dataexchange;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EduImportSnapshotUtilsTest {

    @Test
    void matches_ignoresJsonWhitespaceAndObjectKeyOrder() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("name", "student");
        current.put("state", "NONE");

        assertTrue(EduImportSnapshotUtils.matches("{\"state\": \"NONE\", \"name\": \"student\"}", current));
    }

    @Test
    void matches_ignoresJsonNumberImplementationTypes() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("id", 1L);
        current.put("score", 88.0D);
        current.put("nested", Map.of("studentId", 8L));

        assertTrue(EduImportSnapshotUtils.matches(
                "{\"nested\": {\"studentId\": 8}, \"score\": 88, \"id\": 1}", current));
        assertFalse(EduImportSnapshotUtils.matches(
                "{\"nested\": {\"studentId\": 9}, \"score\": 88, \"id\": 1}", current));
    }

    @Test
    void matches_handlesDatabaseGradebookSnapshot() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("id", 1L);
        current.put("homeworkId", 1L);
        current.put("studentId", 8L);
        current.put("status", 1);
        current.put("score", null);
        current.put("comment", null);
        current.put("submitTime", LocalDateTime.of(2026, 9, 1, 12, 32, 52));

        assertTrue(EduImportSnapshotUtils.matches(
                "{\"id\": 1, \"status\": 1, \"studentId\": 8, \"homeworkId\": 1, "
                        + "\"submitTime\": 1788237172000}", current));
        Map<String, Object> emptyRecord = new LinkedHashMap<>();
        emptyRecord.put("id", null);
        emptyRecord.put("homeworkId", null);
        emptyRecord.put("studentId", null);
        emptyRecord.put("status", null);
        emptyRecord.put("score", null);
        emptyRecord.put("comment", null);
        emptyRecord.put("submitTime", null);
        assertTrue(EduImportSnapshotUtils.matches("{}", emptyRecord));
    }

    @Test
    void matches_handlesNullAndChangedSnapshots() {
        assertTrue(EduImportSnapshotUtils.matches(null, null));
        assertFalse(EduImportSnapshotUtils.matches("{\"state\": \"NONE\"}", null));
        assertFalse(EduImportSnapshotUtils.matches(null, Map.of("state", "NONE")));
        assertFalse(EduImportSnapshotUtils.matches("{\"state\": \"ACTIVE\"}", Map.of("state", "NONE")));
    }

}
