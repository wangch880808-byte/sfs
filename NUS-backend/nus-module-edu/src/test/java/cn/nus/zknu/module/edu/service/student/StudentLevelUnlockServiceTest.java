package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import cn.nus.zknu.module.edu.dal.mysql.student.EduStudentLevelUnlockMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    }

    @Test
    void everyLevelIsAlwaysUnlockedEvenWhenNothingIsDone() {
        List<AppStudentUnlockStatusRespVO.Level> levels = service.getLevels(10L, 20L, false);

        assertEquals(3, levels.size());
        for (AppStudentUnlockStatusRespVO.Level level : levels) {
            assertTrue(level.getUnlocked());
            assertNull(level.getUnlockedAt());
            assertNull(level.getReasonText());
        }
        verify(studentCourseMapper, never()).selectClassLevelTaskCounts(any(), any());
        verifyNoInteractions(unlockMapper);
    }

    @Test
    void levelValidationNeverBlocksStartingATask() {
        rows.add(row(2, 2, 10, null));   // 中级有任务且没达标

        assertDoesNotThrow(() -> service.validateLevelUnlocked(10L, 20L, 2));
        assertDoesNotThrow(() -> service.validateLevelUnlocked(10L, 20L, 3));
        verifyNoInteractions(studentCourseMapper);
        verifyNoInteractions(unlockMapper);
    }

    @Test
    void archivedCourseStillReportsAllLevelsUnlocked() {
        rows.add(row(1, 2, 90, null));

        List<AppStudentUnlockStatusRespVO.Level> levels = service.getLevels(10L, 20L, true);

        assertEquals(3, levels.size());
        assertTrue(levels.stream().allMatch(AppStudentUnlockStatusRespVO.Level::getUnlocked));
        verifyNoInteractions(unlockMapper);
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
