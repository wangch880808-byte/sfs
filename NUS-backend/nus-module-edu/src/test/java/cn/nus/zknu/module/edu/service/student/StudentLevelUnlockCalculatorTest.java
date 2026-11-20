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
    void firstLevelIsAlwaysUnlocked() {
        List<LevelOutcome> outcomes = calculate(true);
        assertTrue(outcomes.get(0).isUnlocked());
        assertNull(outcomes.get(0).getReasonText());
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
    void notYetDueTasksNoLongerDragTheRatioDown() {
        // 旧规则：5 个分配任务、4 个达标 = 80%；再发 1 个没到截止的任务就变成 4/6 而重新上锁
        for (int i = 0; i < 4; i++) task(1, PAST, 2, 80, null);
        task(1, PAST, 2, 30, null);
        task(1, FUTURE, null, null, null);

        LevelOutcome second = calculate(true).get(1);

        assertTrue(second.isUnlocked());
        assertTrue(second.isEarned());
        assertEquals(0.8, second.getRatioAtUnlock(), 1e-9);
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
        task(1, PAST, 4, null, null);   // 批改前就打回过
        task(1, PAST, 1, null, null);

        LevelOutcome first = calculate(true).get(0);
        assertEquals(0, first.passedCount);
        assertEquals(2, first.pendingReviewCount);
    }

    @Test
    void zeroDenominatorWithAssignedTasksKeepsTheNextLevelLocked() {
        task(1, FUTURE, null, null, null);
        task(2, FUTURE, null, null, null);

        List<LevelOutcome> outcomes = calculate(true);

        assertFalse(outcomes.get(1).isUnlocked(), "不能因为暂时没有到期的任务就跳过初级");
        assertEquals("初级任务还没有截止或提交，提交并达标后即可解锁", outcomes.get(1).getReasonText());
        assertFalse(outcomes.get(2).isUnlocked());
        assertEquals("需要先解锁中级", outcomes.get(2).getReasonText());
    }

    @Test
    void lateJoinerStaysLockedWhenThePassIsSwitchedOff() {
        // 插班前初级任务已截止，没有分配给该学生；班级里有初级任务
        classCounts.put(1, 3);
        task(2, FUTURE, null, null, null);

        LevelOutcome second = calculate(true, false).get(1);

        assertFalse(second.isUnlocked());
        assertFalse(second.isEarned());
        assertEquals("你还没有初级任务（可能在加入班级前已截止），请联系教师补发", second.getReasonText());
    }

    @Test
    void lateJoinerWithoutAnyFirstLevelTaskPassesAndGetsASnapshot() {
        classCounts.put(1, 3);
        task(2, FUTURE, null, null, null);

        LevelOutcome second = calculate(true, true).get(1);

        assertTrue(second.isUnlocked());
        assertTrue(second.isEarned(), "插班生放行要写快照，之后教师补发初级任务也不再上锁");
        assertTrue(second.isLateJoinerPass());
        assertNull(second.getRatioAtUnlock(), "没有可计入的任务，快照不带达标比例");
        assertNull(second.getReasonText());
    }

    @Test
    void lateJoinerPassChainsThroughEveryLevelTheStudentMissed() {
        classCounts.put(1, 2);
        classCounts.put(2, 2);

        List<LevelOutcome> outcomes = calculate(true, true);

        assertTrue(outcomes.get(1).isLateJoinerPass());
        assertTrue(outcomes.get(2).isUnlocked());
        assertTrue(outcomes.get(2).isLateJoinerPass());
    }

    @Test
    void lateJoinerWithAssignedFirstLevelTasksMustStillMeetTheRatio() {
        classCounts.put(1, 5);
        task(1, PAST, 2, 90, null);
        task(1, PAST, null, null, null);   // 插班后分配到的初级任务没交

        LevelOutcome second = calculate(true, true).get(1);

        assertFalse(second.isUnlocked());
        assertFalse(second.isLateJoinerPass());
        assertEquals("初级还差 1 个任务达标（需达标 80%）", second.getReasonText());
    }

    @Test
    void lateJoinerWithOnlyOpenFirstLevelTasksWaitsForThem() {
        classCounts.put(1, 5);
        task(1, FUTURE, null, null, null);   // 插班后补发、还没到截止

        LevelOutcome second = calculate(true, true).get(1);

        assertFalse(second.isUnlocked());
        assertEquals("初级任务还没有截止或提交，提交并达标后即可解锁", second.getReasonText());
    }

    @Test
    void personalExtensionKeepsAnOverdueTaskOutOfTheDenominator() {
        for (int i = 0; i < 4; i++) task(1, PAST, 2, 90, null);
        // 任务已过截止，但教师给该学生延期到明天：还不计入分母
        task(1, PAST, null, null, null).setPersonalDeadline(FUTURE);

        LevelOutcome first = calculate(true).get(0);
        assertEquals(4, first.requiredCount);
        assertTrue(calculate(true).get(1).isUnlocked());
    }

    @Test
    void expiredPersonalExtensionCountsTheTask() {
        for (int i = 0; i < 3; i++) task(1, PAST, 2, 90, null);
        task(1, PAST.minusDays(2), null, null, null).setPersonalDeadline(PAST);
        // 早于任务截止时间的延期不起作用
        task(1, PAST, null, null, null).setPersonalDeadline(PAST.minusDays(3));

        LevelOutcome first = calculate(true).get(0);
        assertEquals(5, first.requiredCount);
        assertEquals("初级还差 1 个任务达标（需达标 80%）", calculate(true).get(1).getReasonText());
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
    void levelThatDoesNotExistInTheCourseLetsTheNextLevelThroughWithoutSnapshot() {
        task(2, FUTURE, null, null, null);

        LevelOutcome second = calculate(true).get(1);

        assertTrue(second.isUnlocked());
        assertFalse(second.isEarned(), "没有初级任务时直接放行，但不写快照");
        assertNull(second.getUnlockedAt());
    }

    @Test
    void snapshotKeepsTheLevelUnlockedAfterTheRatioDrops() {
        task(1, PAST, 2, 90, null);
        task(1, PAST, 2, 10, null);
        task(1, PAST, null, null, null);
        EduStudentLevelUnlockDO snapshot = new EduStudentLevelUnlockDO();
        snapshot.setLevel(2);
        snapshot.setUnlockedAt(PAST);
        snapshots.put(2, snapshot);

        LevelOutcome second = calculate(true).get(1);

        assertTrue(second.isUnlocked());
        assertFalse(second.isEarned());
        assertEquals(PAST, second.getUnlockedAt());
        assertNull(second.getReasonText());
    }

    @Test
    void thresholdBoundaryAndMissingCountReason() {
        for (int i = 0; i < 3; i++) task(1, PAST, 2, 60, null);
        task(1, PAST, 2, 59, null);   // 60 分线，差 1 分

        LevelOutcome second = calculate(true).get(1);

        assertFalse(second.isUnlocked());
        assertEquals("初级还差 1 个任务达标（需达标 80%）", second.getReasonText());
    }

    @Test
    void pendingReviewReasonWhenWaitingForTheTeacherIsEnough() {
        for (int i = 0; i < 3; i++) task(1, PAST, 2, 90, null);
        task(1, PAST, 1, null, null);

        assertEquals("有 1 个初级任务在等教师批改，达标后即可解锁", calculate(true).get(1).getReasonText());
    }

    @Test
    void pendingReviewReasonWhenMoreTasksAreMissing() {
        task(1, PAST, 2, 90, null);
        task(1, PAST, 1, null, null);
        task(1, PAST, null, null, null);
        task(1, PAST, null, null, null);
        task(1, PAST, null, null, null);

        assertEquals("初级还差 3 个任务达标，其中 1 个在等教师批改", calculate(true).get(1).getReasonText());
    }

    @Test
    void redoReasonPointsToTheResubmission() {
        task(1, PAST, 2, 90, null);
        task(1, PAST, 3, 20, 20);

        assertEquals("初级还差 1 个任务达标，有 1 个已打回的任务可以重交", calculate(true).get(1).getReasonText());
    }

    @Test
    void thirdLevelUsesTheSecondLevelGate() {
        for (int i = 0; i < 5; i++) task(1, PAST, 2, 90, null);
        task(2, PAST, 2, 90, null);
        task(2, PAST, 2, 20, null);

        List<LevelOutcome> outcomes = calculate(true);

        assertTrue(outcomes.get(1).isUnlocked());
        assertFalse(outcomes.get(2).isUnlocked());
        assertEquals("中级还差 1 个任务达标（需达标 80%）", outcomes.get(2).getReasonText());
    }

    @Test
    void allReasonTextsStayWithinSixtyCharacters() {
        classCounts.put(1, 1);
        for (int i = 0; i < 20; i++) task(1, PAST, null, null, null);
        for (int i = 0; i < 20; i++) task(1, PAST, 1, null, null);
        for (LevelOutcome outcome : calculate(true)) {
            if (outcome.getReasonText() != null) {
                assertTrue(outcome.getReasonText().length() <= 60, outcome.getReasonText());
            }
        }
    }

    private List<LevelOutcome> calculate(boolean keepScoreDuringRedo) {
        return calculate(keepScoreDuringRedo, true);
    }

    private List<LevelOutcome> calculate(boolean keepScoreDuringRedo, boolean lateJoinerPass) {
        return StudentLevelUnlockCalculator.calculate(rows, classCounts, snapshots, THRESHOLD,
                keepScoreDuringRedo, lateJoinerPass, NOW);
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
