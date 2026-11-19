package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.dal.dataobject.student.EduStudentLevelUnlockDO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import cn.nus.zknu.module.edu.service.student.StudentLevelUnlockCalculator.LevelOutcome;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudentLevelUnlockCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);
    private static final LocalDateTime PAST = NOW.minusDays(1);
    private static final LocalDateTime FUTURE = NOW.plusDays(1);
    private static final double THRESHOLD = 0.8;

    private final List<StudentLevelTaskRow> rows = new ArrayList<>();
    private final Map<Integer, Integer> classCounts = new HashMap<>();
    private final Map<Integer, EduStudentLevelUnlockDO> snapshots = new HashMap<>();
    private long nextId = 1;

    @Test
    void allLevelsAreUnlockedWithoutAnyTasks() {
        List<LevelOutcome> outcomes = calculate(true);

        assertEquals(StudentLevelUnlockCalculator.MAX_LEVEL, outcomes.size());
        for (LevelOutcome outcome : outcomes) {
            assertTrue(outcome.isUnlocked(), "等级门槛已取消：" + outcome.level);
            assertFalse(outcome.isEarned());
            assertNull(outcome.getReasonText());
            assertNull(outcome.getUnlockedAt());
        }
    }

    @Test
    void failedRatioDoesNotLockTheNextLevel() {
        classCounts.put(1, 10);
        task(1, PAST, 2, 30, null); // 未达标（30 < 60）
        task(1, PAST, 2, 80, null); // 达标（80 >= 60），本等级达标比例 0.5
        task(2, FUTURE, null, null, null);

        List<LevelOutcome> outcomes = calculate(true);

        assertEquals(0.5, outcomes.get(0).getRatio(), 1e-9);
        assertTrue(outcomes.get(1).isUnlocked(), "初级没达标也不锁中级");
        assertTrue(outcomes.get(2).isUnlocked(), "中级没达标也不锁高级");
        assertNull(outcomes.get(1).getReasonText());
        assertNull(outcomes.get(2).getReasonText());
    }

    @Test
    void zeroDenominatorDoesNotLockTheNextLevel() {
        task(1, FUTURE, null, null, null);
        task(2, FUTURE, null, null, null);

        List<LevelOutcome> outcomes = calculate(true);

        assertEquals(0, outcomes.get(0).requiredCount);
        assertTrue(outcomes.get(1).isUnlocked());
        assertTrue(outcomes.get(2).isUnlocked());
    }

    @Test
    void denominatorCountsOnlyDueOrSubmittedTasks() {
        task(1, FUTURE, null, null, null);      // 未截止未提交：不计
        task(1, PAST, null, null, null);        // 已截止未提交：计入、未达标
        task(1, FUTURE, 2, 90, null);           // 未截止已批改达标：计入
        task(1, FUTURE, 1, null, null);         // 未截止已提交待批改：计入、待批改

        LevelOutcome first = calculate(true).get(0);

        assertEquals(4, first.assignedCount);
        assertEquals(3, first.requiredCount);
        assertEquals(1, first.passedCount);
        assertEquals(1, first.pendingReviewCount);
        assertEquals(1D / 3, first.ratio, 1e-9);
    }

    @Test
    void redoAndResubmitKeepThePreviousPassingScoreWhenEnabled() {
        task(1, PAST, 3, 85, 85);   // 打回中，上次 85 分达标
        task(1, PAST, 4, 85, 85);   // 重交待批改，上次 85 分达标

        LevelOutcome kept = calculate(true).get(0);
        assertEquals(2, kept.passedCount);
        assertEquals(0, kept.pendingReviewCount);

        LevelOutcome dropped = calculate(false).get(0);
        assertEquals(0, dropped.passedCount);
        assertEquals(1, dropped.pendingReviewCount, "重交的记录在等批改");
        assertEquals(1, dropped.redoCount, "打回的记录在等学生重交");
    }

    @Test
    void historyScoreWinsOverRecordScoreAndRecordScoreIsTheFallback() {
        task(1, PAST, 3, 40, 90);    // 轨迹里上次批改 90 分
        task(1, PAST, 3, 90, null);  // 没有轨迹的旧数据，沿用记录上的分数
        task(1, PAST, 3, 90, 40);    // 轨迹里上次批改 40 分

        LevelOutcome first = calculate(true).get(0);
        assertEquals(2, first.passedCount);
        assertEquals(1, first.redoCount);
    }

    @Test
    void resubmittedWithoutPreviousScoreIsPendingNotPassed() {
        task(1, PAST, 4, null, null);
        task(1, PAST, 1, null, null);

        LevelOutcome first = calculate(true).get(0);
        assertEquals(0, first.passedCount);
        assertEquals(2, first.pendingReviewCount);
    }

    @Test
    void personalExtensionKeepsAnOverdueTaskOutOfTheDenominator() {
        for (int i = 0; i < 4; i++) task(1, PAST, 2, 90, null);
        task(1, PAST, null, null, null).setPersonalDeadline(FUTURE);

        assertEquals(4, calculate(true).get(0).requiredCount);
    }

    @Test
    void effectiveDeadlineFollowsTheRedoRules() {
        StudentLevelTaskRow redo = task(1, PAST, 3, 20, 20);
        redo.setPersonalDeadline(FUTURE.plusDays(5));
        redo.setRedoDeadline(FUTURE);
        assertEquals(FUTURE, StudentLevelUnlockCalculator.effectiveDeadline(redo), "重做截止时间优先于个别延期");

        StudentLevelTaskRow redoWithoutDeadline = task(1, PAST, 3, 20, 20);
        redoWithoutDeadline.setPersonalDeadline(FUTURE);
        assertEquals(PAST, StudentLevelUnlockCalculator.effectiveDeadline(redoWithoutDeadline),
                "打回且没设重做截止时间时沿用任务截止时间");

        StudentLevelTaskRow open = task(1, null, null, null, null);
        open.setPersonalDeadline(FUTURE);
        assertNull(StudentLevelUnlockCalculator.effectiveDeadline(open), "任务没有截止时间时不限");
    }

    @Test
    void oldSnapshotsAreIgnoredBecauseNoLevelIsGated() {
        task(1, PAST, 2, 30, null);
        EduStudentLevelUnlockDO snapshot = new EduStudentLevelUnlockDO();
        snapshot.setLevel(2);
        snapshot.setUnlockedAt(PAST);
        snapshots.put(2, snapshot);

        LevelOutcome second = calculate(true).get(1);

        assertTrue(second.isUnlocked());
        assertFalse(second.isEarned(), "不再写入新的解锁快照");
        assertNull(second.getUnlockedAt());
        assertNull(second.getReasonText());
    }

    private List<LevelOutcome> calculate(boolean keepScoreDuringRedo) {
        return StudentLevelUnlockCalculator.calculate(rows, classCounts, snapshots, THRESHOLD,
                keepScoreDuringRedo, true, NOW);
    }

    private StudentLevelTaskRow task(int level, LocalDateTime deadline, Integer status, Integer recordScore,
                      Integer lastCorrectScore) {
        StudentLevelTaskRow row = new StudentLevelTaskRow();
        row.setHomeworkId(nextId++);
        row.setHomeworkLevel(level);
        row.setDeadline(deadline);
        row.setPassScore(60);
        row.setFullScore(100);
        row.setRecordStatus(status);
        row.setRecordId(status == null ? null : row.getHomeworkId());
        row.setRecordScore(recordScore);
        row.setLastCorrectScore(lastCorrectScore);
        rows.add(row);
        return row;
    }
}
