package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.student.EduStudentLevelUnlockDO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import cn.nus.zknu.module.edu.dal.mysql.student.EduStudentLevelUnlockMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_LEVEL_LOCKED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentLevelUnlockServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private StudentLevelUnlockService service;
    @Mock
    private StudentCourseMapper studentCourseMapper;
    @Mock
    private EduStudentLevelUnlockMapper unlockMapper;
    @Mock
    private EduMetricPolicy metricPolicy;
    @Mock
    private ConfigApi configApi;

    private final List<StudentLevelTaskRow> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(metricPolicy.unlockRatio()).thenReturn(0.8);
        lenient().when(metricPolicy.defaultPassScore(any())).thenReturn(60);
        lenient().when(studentCourseMapper.selectLevelTasks(eq(10L), eq(20L), anyBoolean())).thenReturn(rows);
        lenient().when(studentCourseMapper.selectClassLevelTaskCounts(10L, 20L)).thenReturn(List.of());
        lenient().when(unlockMapper.selectListByStudentAndCourse(10L, 20L)).thenReturn(List.of());
    }

    @Test
    void firstEarnedUnlockWritesASnapshotWithTheGateRatio() {
        for (int i = 0; i < 4; i++) rows.add(row(1, 2, 80, null));
        rows.add(row(1, 2, 20, null));

        List<AppStudentUnlockStatusRespVO.Level> levels = service.getLevels(10L, 20L, false);

        assertTrue(levels.get(1).getUnlocked());
        assertNotNull(levels.get(1).getUnlockedAt());
        assertEquals(0.8, levels.get(0).getRatio());
        assertEquals(0.8, levels.get(0).getThreshold());
        assertEquals(5, levels.get(0).getRequiredCount());
        assertEquals(4, levels.get(0).getPassedCount());
        verify(unlockMapper).insertIfAbsent(eq(10L), eq(20L), eq(2), any(LocalDateTime.class),
                eq(new BigDecimal("0.8000")), eq("10"));
        verify(unlockMapper, never()).insertIfAbsent(any(), any(), eq(3), any(), any(), any());
    }

    @Test
    void existingSnapshotIsNotWrittenAgain() {
        rows.add(row(1, 2, 90, null));
        EduStudentLevelUnlockDO snapshot = new EduStudentLevelUnlockDO();
        snapshot.setLevel(2);
        snapshot.setUnlockedAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        when(unlockMapper.selectListByStudentAndCourse(10L, 20L)).thenReturn(List.of(snapshot));

        List<AppStudentUnlockStatusRespVO.Level> levels = service.getLevels(10L, 20L, false);

        assertEquals(snapshot.getUnlockedAt(), levels.get(1).getUnlockedAt());
        verify(unlockMapper, never()).insertIfAbsent(any(), any(), eq(2), any(), any(), any());
    }

    @Test
    void archivedCourseIsReadOnlyAndNeverWritesSnapshots() {
        rows.add(row(1, 2, 90, null));

        List<AppStudentUnlockStatusRespVO.Level> levels = service.getLevels(10L, 20L, true);

        assertTrue(levels.get(1).getUnlocked());
        verify(studentCourseMapper, never()).selectClassLevelTaskCounts(any(), any());
        verify(unlockMapper, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void snapshotWriteFailureDoesNotHideTheUnlock() {
        rows.add(row(1, 2, 90, null));
        when(unlockMapper.insertIfAbsent(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertTrue(service.getLevels(10L, 20L, false).get(1).getUnlocked());
    }

    @Test
    void lockedLevelIsRejectedWithTheReason() {
        rows.add(row(1, 2, 90, null));
        rows.add(row(1, 2, 10, null));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateLevelUnlocked(10L, 20L, 2));

        assertEquals(STUDENT_COURSE_LEVEL_LOCKED.getCode(), ex.getCode());
        assertEquals("中级还没有解锁：初级还差 1 个任务达标（需达标 80%）", ex.getMessage());
    }

    @Test
    void firstLevelAndUngradedLevelsAreNotChecked() {
        service.validateLevelUnlocked(10L, 20L, 1);
        service.validateLevelUnlocked(10L, 20L, 0);
        service.validateLevelUnlocked(10L, 20L, null);
        verifyNoInteractions(studentCourseMapper);
    }

    @Test
    void missingPassScoreFallsBackToTheConfiguredDefault() {
        StudentLevelTaskRow legacy = row(1, 2, 60, null);
        legacy.setPassScore(null);
        rows.add(legacy);

        assertEquals(1, service.getLevels(10L, 20L, false).get(0).getPassedCount());
        verify(metricPolicy).defaultPassScore(100);
    }

    @Test
    void keepScoreDuringRedoReadsTheConfigWithASafeDefault() {
        when(configApi.getConfigValueByKey(StudentLevelUnlockService.KEEP_SCORE_DURING_REDO_KEY))
                .thenReturn(null, "false", "0", "TRUE", "maybe");
        assertTrue(service.keepScoreDuringRedo());
        assertFalse(service.keepScoreDuringRedo());
        assertFalse(service.keepScoreDuringRedo());
        assertTrue(service.keepScoreDuringRedo());
        assertTrue(service.keepScoreDuringRedo());
        when(configApi.getConfigValueByKey(StudentLevelUnlockService.KEEP_SCORE_DURING_REDO_KEY))
                .thenThrow(new RuntimeException("config down"));
        assertTrue(service.keepScoreDuringRedo());
    }

    @Test
    void redoUsesTheHistoryScoreOnlyWhenEnabled() {
        rows.add(row(1, 3, 90, 90));
        when(configApi.getConfigValueByKey(StudentLevelUnlockService.KEEP_SCORE_DURING_REDO_KEY)).thenReturn("false");

        AppStudentUnlockStatusRespVO.Level first = service.getLevels(10L, 20L, false).get(0);

        assertEquals(0, first.getPassedCount());
        assertEquals(1, first.getRequiredCount());
    }

    @Test
    void lateJoinerPassUnlocksAndWritesASnapshotByDefault() {
        rows.add(row(2, null, null, null));
        when(studentCourseMapper.selectClassLevelTaskCounts(10L, 20L))
                .thenReturn(List.of(Map.of("homeworkLevel", 1, "taskCount", 2L)));

        AppStudentUnlockStatusRespVO.Level second = service.getLevels(10L, 20L, false).get(1);

        assertTrue(second.getUnlocked());
        assertNull(second.getReasonText());
        assertNotNull(second.getUnlockedAt());
        // 快照不带达标比例；写入后教师补发初级任务也不会再上锁
        verify(unlockMapper).insertIfAbsent(eq(10L), eq(20L), eq(2), any(LocalDateTime.class), isNull(), eq("10"));
    }

    @Test
    void lateJoinerStaysLockedWhenThePassIsSwitchedOff() {
        rows.add(row(2, null, null, null));
        when(studentCourseMapper.selectClassLevelTaskCounts(10L, 20L))
                .thenReturn(List.of(Map.of("homeworkLevel", 1, "taskCount", 2L)));
        when(configApi.getConfigValueByKey(StudentLevelUnlockService.LATE_JOINER_PASS_KEY)).thenReturn("false");

        AppStudentUnlockStatusRespVO.Level second = service.getLevels(10L, 20L, false).get(1);

        assertFalse(second.getUnlocked());
        assertTrue(second.getReasonText().contains("请联系教师补发"));
        verify(unlockMapper, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void lateJoinerWithAssignedTasksIsJudgedByTheRatio() {
        rows.add(row(1, 2, 90, null));
        rows.add(row(1, null, null, null));
        when(studentCourseMapper.selectClassLevelTaskCounts(10L, 20L))
                .thenReturn(List.of(Map.of("homeworkLevel", 1, "taskCount", 6L)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateLevelUnlocked(10L, 20L, 2));

        assertEquals("中级还没有解锁：初级还差 1 个任务达标（需达标 80%）", ex.getMessage());
        verify(unlockMapper, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void lateJoinerPassReadsTheConfigWithASafeDefault() {
        when(configApi.getConfigValueByKey(StudentLevelUnlockService.LATE_JOINER_PASS_KEY))
                .thenReturn(null, " ", "false", "0", "1", "yes");
        assertTrue(service.lateJoinerPass());
        assertTrue(service.lateJoinerPass());
        assertFalse(service.lateJoinerPass());
        assertFalse(service.lateJoinerPass());
        assertTrue(service.lateJoinerPass());
        assertTrue(service.lateJoinerPass(), "无效值回退为默认的放行");
        when(configApi.getConfigValueByKey(StudentLevelUnlockService.LATE_JOINER_PASS_KEY))
                .thenThrow(new RuntimeException("config down"));
        assertTrue(service.lateJoinerPass());
    }

    @Test
    void personalExtensionIsPassedThroughToTheDenominator() {
        rows.add(row(1, 2, 90, null));
        StudentLevelTaskRow extended = row(1, null, null, null);
        extended.setPersonalDeadline(LocalDateTime.now().plusDays(2));
        rows.add(extended);

        AppStudentUnlockStatusRespVO.Level first = service.getLevels(10L, 20L, false).get(0);

        assertEquals(1, first.getRequiredCount(), "延期还没到的任务不计入分母");
        assertEquals(2, first.getTotalTasks());
    }

    private static StudentLevelTaskRow row(int level, Integer status, Integer score, Integer lastCorrectScore) {
        StudentLevelTaskRow row = new StudentLevelTaskRow();
        row.setHomeworkLevel(level);
        row.setDeadline(LocalDateTime.now().minusDays(1));
        row.setPassScore(60);
        row.setFullScore(100);
        row.setRecordStatus(status);
        row.setRecordScore(score);
        row.setLastCorrectScore(lastCorrectScore);
        return row;
    }
}
