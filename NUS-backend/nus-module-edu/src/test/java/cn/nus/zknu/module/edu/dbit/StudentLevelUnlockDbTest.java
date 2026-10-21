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

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_LEVEL_LOCKED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等级闯关：第一次达标写入快照，之后调高解锁比例或新发低等级任务都不再收回。
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
    void unlockedLevel_staysUnlocked_afterRatioRaisedAndNewLowerLevelTaskPublished() throws Exception {
        long teacherId = newUserId();
        Scope scope = newScope(teacherId);
        long student = newUserId();
        long control = newUserId(); // 成绩完全相同、但之前没有解锁过的同学
        joinClass(scope, student);
        joinClass(scope, control);
        LocalDateTime past = LocalDateTime.now().minusDays(2);
        long basic1 = homework(scope).level(1).deadline(past).create();
        long basic2 = homework(scope).level(1).deadline(past).create();
        long middle = homework(scope).level(2).deadline(LocalDateTime.now().plusDays(5)).create();
        for (long homeworkId : List.of(basic1, basic2, middle)) {
            assign(homeworkId, scope, student);
            assign(homeworkId, scope, control);
        }
        for (long studentId : List.of(student, control)) {
            insertRecord(basic1, studentId, EduHomeworkRecordStatusEnum.CORRECTED, 90, past.minusDays(1));
            insertRecord(basic2, studentId, EduHomeworkRecordStatusEnum.CORRECTED, 80, past.minusDays(1));
        }

        // 第一次查看：初级 2/2 达标 ≥ 0.8，中级解锁并写快照；两个请求同时到达也只写一行
        Callable<List<AppStudentUnlockStatusRespVO.Level>> view = () -> unlockService.getLevels(student,
                scope.courseId(), false);
        List<Outcome<List<AppStudentUnlockStatusRespVO.Level>>> first = runConcurrently(List.of(view, view));
        assertTrue(first.stream().allMatch(Outcome::ok), () -> "并发查看不应失败：" + first);
        AppStudentUnlockStatusRespVO.Level middleLevel = first.get(0).value().get(1);
        assertTrue(middleLevel.getUnlocked());
        assertEquals(1, snapshotCount(student, scope.courseId(), 2));
        LocalDateTime unlockedAt = jdbc.queryForObject("SELECT unlocked_at FROM edu_student_level_unlock "
                + "WHERE student_id = ? AND course_id = ? AND level = 2", LocalDateTime.class, student, scope.courseId());
        assertNotNull(unlockedAt);

        // 之后：比例调到 1.0，并新发一个已截止、未提交的初级任务（初级变成 2/3）
        setRatio("1.0");
        long basic3 = homework(scope).level(1).deadline(LocalDateTime.now().minusHours(1)).create();
        assign(basic3, scope, student);
        assign(basic3, scope, control);

        List<AppStudentUnlockStatusRespVO.Level> again = unlockService.getLevels(student, scope.courseId(), false);
        AppStudentUnlockStatusRespVO.Level basicNow = again.get(0);
        assertEquals(3, basicNow.getRequiredCount());
        assertEquals(2, basicNow.getPassedCount());
        assertEquals(1.0, basicNow.getThreshold());
        AppStudentUnlockStatusRespVO.Level middleNow = again.get(1);
        assertTrue(middleNow.getUnlocked(), "已解锁的等级不应收回");
        assertEquals(unlockedAt, middleNow.getUnlockedAt(), "解锁时间沿用快照");
        assertEquals(1, snapshotCount(student, scope.courseId(), 2), "不应重复写快照");
        assertDoesNotThrow(() -> unlockService.validateLevelUnlocked(student, scope.courseId(), 2));

        // 对照：同样成绩、之前没解锁过的同学按新规则仍未解锁，也不会写快照
        AppStudentUnlockStatusRespVO.Level controlMiddle = unlockService.getLevels(control, scope.courseId(), false)
                .get(1);
        assertFalse(controlMiddle.getUnlocked());
        assertNotNull(controlMiddle.getReasonText());
        assertEquals(0, snapshotCount(control, scope.courseId(), 2));
        assertServiceError(STUDENT_COURSE_LEVEL_LOCKED, assertThrows(RuntimeException.class,
                () -> unlockService.validateLevelUnlocked(control, scope.courseId(), 2)));
    }

    @Test
    void historyView_doesNotWriteSnapshot() {
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

        assertTrue(levels.get(1).getUnlocked());
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
