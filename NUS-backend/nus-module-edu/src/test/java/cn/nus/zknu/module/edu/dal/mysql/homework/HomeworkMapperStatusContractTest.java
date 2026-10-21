package cn.nus.zknu.module.edu.dal.mysql.homework;

import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** SQL 保留字面量；常量或查询口径变更时必须同步核对任务、提交和 AI 初评状态。 */
class HomeworkMapperStatusContractTest {

    private static final Pattern STATUS_LITERAL = Pattern.compile(
            "\\b(?:h|owned|hr|r|ar)\\.status\\s*(?:IN\\s*\\([0-9,\\s]+\\)|(?:!=|<>|>=|<=|=|>|<)\\s*[0-9]+)"
                    + "|COALESCE\\(hr\\.status,\\s*[0-9]+\\)|\\bstatus\\s*==\\s*[0-9]+",
            Pattern.CASE_INSENSITIVE);

    @Test
    void persistedStatusValuesAndReviewListsRemainCompatible() {
        assertEquals(List.of(0, 1, 2, 3), List.of(EduHomeworkStatusEnum.DRAFT,
                EduHomeworkStatusEnum.PUBLISHED, EduHomeworkStatusEnum.CLOSED, EduHomeworkStatusEnum.ARCHIVED));
        assertEquals(List.of(0, 1, 2, 3, 4), List.of(EduHomeworkRecordStatusEnum.NOT_SUBMITTED,
                EduHomeworkRecordStatusEnum.SUBMITTED, EduHomeworkRecordStatusEnum.CORRECTED,
                EduHomeworkRecordStatusEnum.REDO, EduHomeworkRecordStatusEnum.RESUBMITTED));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), List.of(HomeworkAiReviewStatusEnum.QUEUED,
                HomeworkAiReviewStatusEnum.PROCESSING, HomeworkAiReviewStatusEnum.READY,
                HomeworkAiReviewStatusEnum.FAILED, HomeworkAiReviewStatusEnum.ACCEPTED,
                HomeworkAiReviewStatusEnum.REJECTED, HomeworkAiReviewStatusEnum.SKIPPED));
        assertEquals(List.of(EduHomeworkRecordStatusEnum.SUBMITTED, EduHomeworkRecordStatusEnum.RESUBMITTED),
                EduHomeworkRecordStatusEnum.PENDING_REVIEW);
        assertEquals(List.of(EduHomeworkRecordStatusEnum.SUBMITTED, EduHomeworkRecordStatusEnum.CORRECTED,
                EduHomeworkRecordStatusEnum.RESUBMITTED), EduHomeworkRecordStatusEnum.CORRECTABLE);
    }

    @Test
    void studentCourseXmlStatusLiteralsMatchConstants() throws IOException {
        assertLiterals(xml("student/StudentCourseMapper.xml"), Map.of(
                activeHomework(), 3, archivedHomework(), 1, unsubmittedDefault(), 1, correctedRecord(), 1));
    }

    @Test
    void studentHomeworkXmlStatusLiteralsMatchConstants() throws IOException {
        assertLiterals(xml("student/StudentHomeworkMapper.xml"), Map.of(
                activeHomework(), 3, archivedHomework(), 1, unsubmittedDefault(), 1,
                "status == " + EduHomeworkRecordStatusEnum.NOT_SUBMITTED, 1));
    }

    @Test
    void studentCapacityXmlStatusLiteralsMatchConstants() throws IOException {
        assertLiterals(xml("student/StudentCapacityMapper.xml"), Map.of(
                activeHomework(), 1, archivedHomework(), 1, submittedRecord(), 3,
                pendingRecord("hr"), 3, correctedRecord(), 9));
    }

    @Test
    void statisticsXmlStatusLiteralsMatchConstants() throws IOException {
        assertLiterals(xml("statistics/StatisticsMapper.xml"), Map.of(
                activeHomework(), 5, "h.status IN (" + EduHomeworkStatusEnum.PUBLISHED + ", "
                        + EduHomeworkStatusEnum.CLOSED + ", " + EduHomeworkStatusEnum.ARCHIVED + ")", 7,
                "owned.status IN (" + EduHomeworkStatusEnum.PUBLISHED + ", "
                        + EduHomeworkStatusEnum.CLOSED + ", " + EduHomeworkStatusEnum.ARCHIVED + ")", 1,
                correctedRecord(), 6, submittedRecord(), 1, pendingRecord("hr"), 1,
                "ar.status IN (" + HomeworkAiReviewStatusEnum.READY + ", "
                        + HomeworkAiReviewStatusEnum.ACCEPTED + ", " + HomeworkAiReviewStatusEnum.REJECTED + ")", 1));
    }

    @Test
    void digestAnnotationStatusLiteralsMatchConstants() throws NoSuchMethodException {
        Select select = EduHomeworkNotifyStateMapper.class.getMethod("selectPendingDigests",
                LocalDateTime.class, LocalDateTime.class).getAnnotation(Select.class);
        assertNotNull(select);
        assertLiterals(String.join(" ", select.value()), Map.of(
                "r.status = " + EduHomeworkRecordStatusEnum.RESUBMITTED, 1,
                activeHomework(), 1, pendingRecord("r"), 1));
    }

    @Test
    void failedReviewAnnotationStatusLiteralsMatchConstants() throws NoSuchMethodException {
        Select select = EduHomeworkAiReviewMapper.class.getMethod("countLatestFailedByHomeworkId",
                Long.class).getAnnotation(Select.class);
        assertNotNull(select);
        assertLiterals(String.join(" ", select.value()), Map.of(
                "ar.status = " + HomeworkAiReviewStatusEnum.FAILED, 1, pendingRecord("r"), 1));
    }

    @Test
    void autoLinkArchiveAnnotationsUseTheSameStatusContract() throws NoSuchMethodException {
        Map<String, Integer> expected = Map.of(
                "h.status = " + EduHomeworkStatusEnum.PUBLISHED, 1, pendingRecord("r"), 1, activeHomework(), 1);
        Update outsideSemester = EduClassCourseMapper.class.getMethod("archiveIdleAutoLinksOutsideSemester",
                String.class).getAnnotation(Update.class);
        Update offlineCourse = EduClassCourseMapper.class.getMethod("archiveIdleAutoLinksOfOfflineCourses",
                Long.class).getAnnotation(Update.class);
        assertNotNull(outsideSemester);
        assertNotNull(offlineCourse);
        assertLiterals(String.join(" ", outsideSemester.value()), expected);
        assertLiterals(String.join(" ", offlineCourse.value()), expected);
    }

    private String xml(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/mapper/" + path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertLiterals(String sql, Map<String, Integer> expected) {
        Map<String, Integer> actual = new LinkedHashMap<>();
        Matcher matcher = STATUS_LITERAL.matcher(sql.replaceAll("(?s)<!--.*?-->", ""));
        while (matcher.find()) {
            actual.merge(normalize(matcher.group()), 1, Integer::sum);
        }
        Map<String, Integer> normalizedExpected = new LinkedHashMap<>();
        expected.forEach((predicate, count) -> normalizedExpected.put(normalize(predicate), count));
        assertEquals(normalizedExpected, actual, "SQL 状态字面量或口径变化时，请同步核对状态常量");
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String activeHomework() {
        return "h.status IN (" + EduHomeworkStatusEnum.PUBLISHED + ", " + EduHomeworkStatusEnum.CLOSED + ")";
    }

    private String archivedHomework() {
        return "h.status = " + EduHomeworkStatusEnum.ARCHIVED;
    }

    private String unsubmittedDefault() {
        return "COALESCE(hr.status, " + EduHomeworkRecordStatusEnum.NOT_SUBMITTED + ")";
    }

    private String correctedRecord() {
        return "hr.status = " + EduHomeworkRecordStatusEnum.CORRECTED;
    }

    private String submittedRecord() {
        return "hr.status IN (" + EduHomeworkRecordStatusEnum.SUBMITTED + ", "
                + EduHomeworkRecordStatusEnum.CORRECTED + ", " + EduHomeworkRecordStatusEnum.RESUBMITTED + ")";
    }

    private String pendingRecord(String alias) {
        return alias + ".status IN (" + EduHomeworkRecordStatusEnum.SUBMITTED + ", "
                + EduHomeworkRecordStatusEnum.RESUBMITTED + ")";
    }
}
