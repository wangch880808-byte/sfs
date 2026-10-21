package cn.nus.zknu.module.edu.dbit;

import cn.nus.zknu.framework.common.util.spring.SpringUtils;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkNotifyStateMapper;
import cn.nus.zknu.module.edu.dbit.support.EduDbIntegrationTest;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseBackfillService;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncService;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkCopyService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import cn.nus.zknu.module.edu.service.semester.EduTermFlowPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用完整 V21 数据库验证同步通知与认领补发；默认测试排除 db 标签。 */
@Import({EduPublicCourseSyncService.class, EduPublicCourseBackfillService.class,
        EduPublicCourseSyncScheduler.class, PublicCourseBackfillDbTest.InlineSyncConfiguration.class})
class PublicCourseBackfillDbTest extends EduDbIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private EduPublicCourseSyncService syncService;
    @Autowired
    private EduPublicCourseSyncScheduler syncScheduler;
    @MockitoSpyBean
    private EduPublicCourseBackfillService backfillService;
    @Autowired
    private EduHomeworkNotifyStateMapper notifyStateMapper;
    @MockitoBean
    private EduSemesterService semesterService;
    @MockitoBean
    private EduTermFlowPolicy termFlowPolicy;
    @MockitoBean
    private EduHomeworkCopyService copyService;

    @BeforeEach
    void batchInsertUsesThisTestDataSource() {
        assertSame(dataSource, SpringUtils.getBean(DataSource.class),
                "insertBatch 的静态 bean 查找必须指向当前测试上下文的数据源");
    }

    @Test
    void afterCommitSchedulerWithInlineExecutorKeepsBackfillTransactionalAndIdempotent() {
        Scope original = newScope(newUserId());
        String semester = "backfill-async-" + original.courseId();
        preparePublicCourse(original, semester);
        homework(original).semester(semester).create();
        Scope added = newClass(original);
        joinClass(added, newUserId());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            syncScheduler.requestClass(added.classId());
            assertEquals(0, linkCount(added, semester));
        });
        syncScheduler.requestClass(added.classId());

        assertEquals(1, linkCount(added, semester));
        assertEquals(0, taskCount(added, semester));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM edu_class_course WHERE course_id = ?"
                + " AND class_id = ? AND semester_name = ? AND backfill_pending = 1", Integer.class,
                original.courseId(), added.classId(), semester));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM edu_homework_assignee a"
                + " JOIN edu_homework h ON h.id = a.homework_id WHERE h.class_id = ?"
                + " AND h.course_id = ? AND h.semester_name = ? AND a.deleted = 0",
                Integer.class, added.classId(), added.courseId(), semester));
    }

    @Test
    void concurrentLinkSyncRetriesCommittedBatchBeforeExpandingCoverage() throws Exception {
        Scope original = newScope(newUserId());
        String semester = "backfill-race-" + original.courseId();
        preparePublicCourse(original, semester);
        homework(original).semester(semester).create();
        Scope first = newClass(original);
        Scope second = newClass(original);
        joinClass(first, newUserId());
        joinClass(second, newUserId());
        CountDownLatch linkCommitted = new CountDownLatch(1);
        CountDownLatch resumeFirstCallback = new CountDownLatch(1);
        AtomicBoolean firstCallback = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstCallback.compareAndSet(true, false)) {
                linkCommitted.countDown();
                assertTrue(resumeFirstCallback.await(10, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(backfillService).backfill(eq(original.courseId()), eq(semester), any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var firstSync = executor.submit(() -> syncService.syncClass(first.classId()));
            assertTrue(linkCommitted.await(10, TimeUnit.SECONDS));
            assertEquals(1, linkCount(first, semester));
            // A 已提交、通知未启动；B 先完成待通知批次，不复制任务。
            syncService.syncClass(second.classId());
            assertEquals(0, linkCount(second, semester));
            assertEquals(0, taskCount(first, semester));
            resumeFirstCallback.countDown();
            firstSync.get(10, TimeUnit.SECONDS);
            syncService.syncClass(second.classId());
            syncService.syncClass(first.classId());
            syncService.syncClass(second.classId());

            assertEquals(0, taskCount(first, semester));
            assertEquals(0, taskCount(second, semester));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM edu_homework_assignee a"
                    + " JOIN edu_homework h ON h.id = a.homework_id WHERE h.course_id = ?"
                    + " AND h.semester_name = ? AND a.deleted = 0", Integer.class, original.courseId(), semester));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM edu_class_course"
                    + " WHERE course_id = ? AND semester_name = ? AND backfill_pending = 1",
                    Integer.class, original.courseId(), semester));
        } finally {
            resumeFirstCallback.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void committedLinkSurvivesFailedBackfillAndNextSyncRetriesIt() {
        Scope original = newScope(newUserId());
        String semester = "backfill-retry-" + original.courseId();
        preparePublicCourse(original, semester);
        homework(original).semester(semester).create();
        Scope added = newClass(original);
        joinClass(added, newUserId());
        doThrow(new IllegalStateException("临时通知故障")).doCallRealMethod()
                .when(backfillService).backfill(eq(original.courseId()), eq(semester), any());

        assertDoesNotThrow(() -> syncService.syncClass(added.classId()));
        verifyNoInteractions(copyService);
        assertEquals(1, linkCount(added, semester));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM edu_class_course WHERE course_id = ?"
                + " AND class_id = ? AND semester_name = ? AND backfill_pending = 1", Integer.class,
                original.courseId(), added.classId(), semester));
        assertEquals(0, taskCount(added, semester));

        syncService.syncClass(added.classId());
        syncService.syncClass(added.classId());

        assertEquals(0, taskCount(added, semester));
        verifyNoInteractions(copyService);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM edu_class_course WHERE course_id = ?"
                + " AND class_id = ? AND semester_name = ? AND backfill_pending = 1", Integer.class,
                original.courseId(), added.classId(), semester));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM edu_homework_assignee a"
                + " JOIN edu_homework h ON h.id = a.homework_id WHERE h.class_id = ?"
                + " AND h.course_id = ? AND h.semester_name = ? AND a.deleted = 0",
                Integer.class, added.classId(), added.courseId(), semester));
    }

    @Test
    void claimReplicatesOwnSourceTaskOnceAndKeepsUpdater() {
        Scope original = newScope(newUserId());
        String semester = "claim-backfill-" + original.courseId();
        preparePublicCourse(original, semester);
        homework(original).semester(semester).create();
        jdbc.update("UPDATE edu_class_course SET claim_teacher_id = ? WHERE course_id = ? AND class_id = ?",
                original.teacherId(), original.courseId(), original.classId());
        Scope added = newClass(original);
        joinClass(added, newUserId());
        syncService.syncClass(added.classId());
        assertEquals(0, taskCount(added, semester));
        jdbc.update("UPDATE edu_class_course SET claim_teacher_id = ?, claim_time = NOW() WHERE course_id = ? AND class_id = ?",
                original.teacherId(), added.courseId(), added.classId());
        backfillService.backfillForClaim(added.courseId(), semester, original.teacherId(), added.classId());
        backfillService.backfillForClaim(added.courseId(), semester, original.teacherId(), added.classId());
        assertEquals(1, taskCount(added, semester));
        assertEquals("system", jdbc.queryForObject("SELECT updater FROM edu_class_course WHERE course_id = ? AND class_id = ?",
                String.class, added.courseId(), added.classId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM edu_homework_assignee a JOIN edu_homework h ON h.id=a.homework_id"
                + " WHERE h.course_id=? AND h.class_id=? AND a.deleted=0", Integer.class, added.courseId(), added.classId()));
    }

    @Test
    void fiveMinuteDigestWithTenMinuteScansIncludesFirstSubmissionInFormerGap() {
        Scope scope = newScope(newUserId());
        long homeworkId = homework(scope).create();
        LocalDateTime firstCutoff = LocalDateTime.of(2026, 10, 10, 9, 58);
        LocalDateTime secondCutoff = firstCutoff.plusMinutes(10);
        notifyStateMapper.initializeDigestCursors(firstCutoff.minusMinutes(5));
        notifyStateMapper.advanceEmptyDigestCursors(firstCutoff);
        insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.SUBMITTED, null,
                LocalDateTime.of(2026, 10, 10, 10, 0));
        notifyStateMapper.initializeDigestCursors(secondCutoff.minusMinutes(5));
        notifyStateMapper.advanceEmptyDigestCursors(secondCutoff);

        var row = notifyStateMapper.selectPendingDigests(secondCutoff, secondCutoff.minusMinutes(3)).stream()
                .filter(item -> item.getHomeworkId() == homeworkId).findFirst().orElseThrow();
        assertEquals(firstCutoff, row.getDigestUntil());
        assertEquals(1L, row.getSubmittedCount());
        LocalDateTime sentAt = secondCutoff.plusMinutes(2);
        assertEquals(1, notifyStateMapper.advanceDigest(homeworkId, firstCutoff, secondCutoff,
                sentAt, sentAt.minusMinutes(5)));
        assertEquals(0, notifyStateMapper.advanceDigest(homeworkId, firstCutoff, secondCutoff,
                sentAt, sentAt.minusMinutes(5)));
        // 空扫描推进不能影响限频；限频期间的提交保留在原游标之后。
        LocalDateTime thirdCutoff = secondCutoff.plusMinutes(1);
        notifyStateMapper.advanceEmptyDigestCursors(thirdCutoff);
        insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.RESUBMITTED, null,
                thirdCutoff.plusSeconds(30));
        LocalDateTime gatedCutoff = thirdCutoff.plusMinutes(1);
        notifyStateMapper.advanceEmptyDigestCursors(gatedCutoff);
        assertEquals(thirdCutoff, notifyStateMapper.selectByHomeworkId(homeworkId).getDigestUntil());
        assertTrue(notifyStateMapper.selectPendingDigests(gatedCutoff, sentAt.minusMinutes(4)).stream()
                .noneMatch(item -> item.getHomeworkId() == homeworkId));
        var later = notifyStateMapper.selectPendingDigests(gatedCutoff.plusMinutes(5), sentAt).stream()
                .filter(item -> item.getHomeworkId() == homeworkId).findFirst().orElseThrow();
        assertEquals(1L, later.getSubmittedCount());
        assertEquals(1L, later.getResubmittedCount());
    }

    @Test
    void newlyPublishedHomeworkUsesPreviousGlobalScanBoundary() {
        LocalDateTime firstCutoff = LocalDateTime.of(2026, 10, 10, 11, 58);
        LocalDateTime nextCutoff = firstCutoff.plusMinutes(10);
        notifyStateMapper.initializeGlobalDigestCursor(firstCutoff.minusMinutes(5));
        // 单行共享状态是本测试的受控数据；使本例与其他测试的执行顺序无关。
        jdbc.update("UPDATE edu_homework_digest_scan SET scan_until = ? WHERE id = 1", firstCutoff);
        Scope scope = newScope(newUserId());
        long homeworkId = homework(scope).create();
        insertRecord(homeworkId, newUserId(), EduHomeworkRecordStatusEnum.SUBMITTED, null,
                firstCutoff.plusMinutes(2));
        notifyStateMapper.initializeGlobalDigestCursor(nextCutoff.minusMinutes(5));
        notifyStateMapper.initializeDigestCursors(notifyStateMapper.selectGlobalDigestCursor());
        notifyStateMapper.advanceEmptyDigestCursors(nextCutoff);

        var row = notifyStateMapper.selectPendingDigests(nextCutoff, nextCutoff.minusMinutes(3)).stream()
                .filter(item -> item.getHomeworkId() == homeworkId).findFirst().orElseThrow();
        assertEquals(firstCutoff, row.getDigestUntil());
        assertEquals(1L, row.getSubmittedCount());
        notifyStateMapper.advanceGlobalDigestCursor(nextCutoff);
        notifyStateMapper.advanceGlobalDigestCursor(firstCutoff);
        assertEquals(nextCutoff, notifyStateMapper.selectGlobalDigestCursor());
    }

    private void preparePublicCourse(Scope scope, String semester) {
        jdbc.update("UPDATE capacity_course SET is_public_course = 0, public_audit_status = 2 WHERE id = ?",
                scope.courseId());
        jdbc.update("UPDATE edu_class_course SET semester_name = ? WHERE course_id = ?", semester, scope.courseId());
        EduSemesterRespVO current = new EduSemesterRespVO();
        current.setSemesterName(semester);
        when(semesterService.getCurrentSemester()).thenReturn(current);
        when(termFlowPolicy.publicCourseBackfillEnabled()).thenReturn(true);
        when(copyService.uniqueName(anyString(), any())).thenCallRealMethod();
    }

    private Scope newClass(Scope course) {
        long id = insert("edu_class", Map.of("class_name", "it-backfill-" + newUserId(), "grade_year", 2026,
                "dept_id", 0L, "education_level", 1L, "schooling_year", 4L, "status", 0));
        return new Scope(id, course.courseId(), course.teacherId());
    }

    private int linkCount(Scope scope, String semester) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM edu_class_course WHERE class_id = ?"
                + " AND course_id = ? AND semester_name = ? AND deleted = 0", Integer.class,
                scope.classId(), scope.courseId(), semester);
    }

    private int taskCount(Scope scope, String semester) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM edu_homework WHERE class_id = ?"
                + " AND course_id = ? AND semester_name = ? AND deleted = 0", Integer.class,
                scope.classId(), scope.courseId(), semester);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InlineSyncConfiguration {
        @Bean(name = "eduPublicCourseSyncExecutor")
        Executor publicCourseSyncExecutor() {
            return Runnable::run;
        }
    }
}
