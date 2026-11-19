package cn.nus.zknu.module.edu.dbit;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dbit.support.EduDbIntegrationTest;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.student.StudentLevelUnlockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等级闯关：各等级始终开放，只统计任务进度，不再写解锁快照。
 */
class StudentLevelUnlockDbTest extends EduDbIntegrationTest {

    private static final String RATIO_KEY = "edu.course.unlock-ratio";

    @Autowired
    private StudentLevelUnlockService unlockService;

    private String originalRatio;

    @BeforeEach
    void rememberRatio() {
        originalRatio = jdbc.queryForObject("SELECT value FROM infra_config WHERE config_key = ? AND deleted = 0",
                String.class, RATIO_KEY);
        setRatio("0.8");
    }

    @AfterEach
    void restoreRatio() {
        setRatio(originalRatio);
    }

    @Test
    void allLevelsStayUnlockedWithoutAnyPassingScoreOrSnapshot() throws Exception {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long student = newUserId();
        joinClass(scope, student);
        LocalDateTime past = LocalDateTime.now().minusDays(2);
        long basic = homework(scope).level(1).deadline(past).create();
        long middle = homework(scope).level(2).deadline(LocalDateTime.now().plusDays(5)).create();
        long advanced = homework(scope).level(3).deadline(LocalDateTime.now().plusDays(5)).create();
        for (long homeworkId : List.of(basic, middle, advanced)) {
            assign(homeworkId, scope, student);
        }
        insertRecord(basic, student, EduHomeworkRecordStatusEnum.CORRECTED, 30, past.minusDays(1));

        Callable<List<AppStudentUnlockStatusRespVO.Level>> view = () -> unlockService.getLevels(student,
                scope.courseId(), false);
        var first = runConcurrently(List.of(view, view));
        assertTrue(first.stream().allMatch(Outcome::ok), () -> "并发查看不应失败：" + first);

        List<AppStudentUnlockStatusRespVO.Level> levels = first.get(0).value();
        assertEquals(3, levels.size());
        for (AppStudentUnlockStatusRespVO.Level level : levels) {
            assertTrue(level.getUnlocked(), "等级 " + level.getLevel() + " 应始终开放");
            assertNull(level.getUnlockedAt());
            assertNull(level.getReasonText());
        }
        assertEquals(1, levels.get(0).getRequiredCount());
        assertEquals(0, levels.get(0).getPassedCount());
        assertEquals(0, snapshotCount(student, scope.courseId(), 2));
        assertEquals(0, snapshotCount(student, scope.courseId(), 3));

        assertDoesNotThrow(() -> unlockService.validateLevelUnlocked(student, scope.courseId(), 2));
        assertDoesNotThrow(() -> unlockService.validateLevelUnlocked(student, scope.courseId(), 3));
    }

    @Test
    void archiveViewAlsoReportsAllLevelsUnlocked() {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long student = newUserId();
        joinClass(scope, student);
        LocalDateTime past = LocalDateTime.now().minusDays(2);
        long basic = homework(scope).level(1).deadline(past).create();
        long middle = homework(scope).level(2).deadline(past).create();
        assign(basic, scope, student);
        assign(middle, scope, student);
        insertRecord(basic, student, EduHomeworkRecordStatusEnum.CORRECTED, 95, past.minusDays(1));

        List<AppStudentUnlockStatusRespVO.Level> levels = unlockService.getLevels(student, scope.courseId(), true);

        assertEquals(3, levels.size());
        assertTrue(levels.stream().allMatch(AppStudentUnlockStatusRespVO.Level::getUnlocked));
        assertEquals(0, snapshotCount(student, scope.courseId(), 2), "只读的历史口径不写快照");
    }

    private void setRatio(String value) {
        jdbc.update("UPDATE infra_config SET value = ? WHERE config_key = ? AND deleted = 0", value, RATIO_KEY);
    }

    private long snapshotCount(long studentId, long courseId, int level) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM edu_student_level_unlock WHERE student_id = ? "
                + "AND course_id = ? AND level = ?", Long.class, studentId, courseId, level);
        return value == null ? 0 : value;
    }
}
