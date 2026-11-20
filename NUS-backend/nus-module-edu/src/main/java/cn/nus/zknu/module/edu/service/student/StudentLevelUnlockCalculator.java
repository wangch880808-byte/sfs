package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.dal.dataobject.student.EduStudentLevelUnlockDO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.homework.HomeworkDeadlineUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 等级闯关规则（纯计算，不读写数据库，便于单测）。
 *
 * <h3>每个等级自身的统计</h3>
 * <ul>
 *     <li>分母 requiredCount：分配给学生的该等级任务中，已过学生本人的实际截止时间或学生已提交的任务；
 *     还没到截止时间、也没提交的任务不计入。实际截止时间按 {@link HomeworkDeadlineUtils} 的口径：
 *     任务截止时间与个别延期取较晚的一个，打回重做的记录沿用重做规则。</li>
 *     <li>分子 passedCount：分母中最近一次批改的分数达到任务达标分数线的任务。已批改（2）看提交记录上的分数；
 *     打回（3）、重交（4）期间，开关 {@code edu.unlock.keep-score-during-redo} 打开时沿用上一次批改的分数
 *     （优先取评分轨迹中最近一次批改的分数，没有轨迹的旧数据取提交记录上的分数）。</li>
 *     <li>待批改 pendingReviewCount：已提交（1）或已重交（4）、且没有按上一条计为达标的任务。</li>
 * </ul>
 *
 * <h3>解锁</h3>
 * 初级始终解锁。等级 L（L &gt; 1）的“关卡”是上一等级 P = L - 1：
 * <ol>
 *     <li>已有解锁快照：保持解锁（单向，之后新发任务、打回、调高比例或分数线都不再收回）；</li>
 *     <li>P 本身没有解锁：L 不解锁；</li>
 *     <li>P 在这门课里不存在（学生没有 P 级任务，所在班级也没有发布过 P 级任务）：直接放行，但不写快照，
 *     之后发布了 P 级任务就按 P 的达标情况判断；</li>
 *     <li>插班生：P 存在（所在班级发布过 P 级任务），但一个 P 级任务都没有分配给学生（插班前已截止）。
 *     开关 {@code edu.unlock.late-joiner-pass} 打开（默认）时视为通过，并由调用方写入快照，之后不再上锁；
 *     关闭时保持锁定，提示联系教师补发；</li>
 *     <li>P 有分配给学生的任务但分母为 0（还没有截止或提交的任务）：不解锁，
 *     不能因为“暂时没有可计入的任务”跳过一个等级；</li>
 *     <li>P 的达标比例 passedCount / requiredCount 不低于阈值：解锁，并由调用方写入快照。</li>
 * </ol>
 */
public final class StudentLevelUnlockCalculator {

    public static final int MAX_LEVEL = 3;
    static final String[] LEVEL_NAMES = {"初级", "中级", "高级"};
    private static final double EPSILON = 1e-9;

    private StudentLevelUnlockCalculator() {
    }

    public static String levelName(int level) {
        return level >= 1 && level <= MAX_LEVEL ? LEVEL_NAMES[level - 1] : "该等级";
    }

    /**
     * @param rows               分配给学生的各等级任务（passScore 已补齐默认值）
     * @param classLevelCounts   学生当前所在班级在这门课已发布的各等级任务数
     * @param snapshots          已有解锁快照，按等级
     * @param threshold          解锁比例（0~1）
     * @param keepScoreDuringRedo 打回、重交期间是否沿用上一次批改的分数
     * @param lateJoinerPass     插班生没有分配到上一等级任务时是否视为通过
     * @param now                当前时间（判断是否已过截止时间）
     */
    public static List<LevelOutcome> calculate(Collection<StudentLevelTaskRow> rows,
                                               Map<Integer, Integer> classLevelCounts,
                                               Map<Integer, EduStudentLevelUnlockDO> snapshots,
                                               double threshold, boolean keepScoreDuringRedo,
                                               boolean lateJoinerPass, LocalDateTime now) {
        LevelStats[] stats = new LevelStats[MAX_LEVEL + 1];
        for (int level = 1; level <= MAX_LEVEL; level++) {
            stats[level] = new LevelStats();
        }
        for (StudentLevelTaskRow row : rows) {
            Integer level = row.getHomeworkLevel();
            if (level == null || level < 1 || level > MAX_LEVEL) {
                continue;
            }
            stats[level].add(row, keepScoreDuringRedo, now);
        }

        List<LevelOutcome> outcomes = new ArrayList<>(MAX_LEVEL);
        boolean[] unlocked = new boolean[MAX_LEVEL + 1];
        for (int level = 1; level <= MAX_LEVEL; level++) {
            LevelStats own = stats[level];
            LevelOutcome outcome = new LevelOutcome(level, own.assigned, own.required, own.passed, own.pending,
                    own.redoWaiting, own.ratio(), threshold);
            if (level == 1) {
                outcome.unlocked = true;
            } else {
                evaluateGate(outcome, stats[level - 1], unlocked[level - 1],
                        classLevelCounts.getOrDefault(level - 1, 0), snapshots.get(level), threshold,
                        lateJoinerPass);
            }
            unlocked[level] = outcome.unlocked;
            outcomes.add(outcome);
        }
        return outcomes;
    }

    private static void evaluateGate(LevelOutcome outcome, LevelStats gate, boolean gateUnlocked,
                                     int gateClassTaskCount, EduStudentLevelUnlockDO snapshot, double threshold,
                                     boolean lateJoinerPass) {
        String gateName = levelName(outcome.level - 1);
        if (snapshot != null) {
            outcome.unlocked = true;
            outcome.unlockedAt = snapshot.getUnlockedAt();
            return;
        }
        if (!gateUnlocked) {
            outcome.reasonText = "需要先解锁" + gateName;
            return;
        }
        boolean gateExists = gate.assigned > 0 || gateClassTaskCount > 0;
        if (!gateExists) {
            // 这门课没有上一等级的任务，直接放行；不写快照
            outcome.unlocked = true;
            return;
        }
        if (gate.assigned == 0) {
            // 班级发布过上一等级任务，但一个都没有分配给该学生（插班前已截止）
            if (lateJoinerPass) {
                outcome.unlocked = true;
                outcome.earned = true;
                outcome.lateJoinerPass = true;
                return;
            }
            outcome.reasonText = "你还没有" + gateName + "任务（可能在加入班级前已截止），请联系教师补发";
            return;
        }
        if (gate.required == 0) {
            outcome.reasonText = gateName + "任务还没有截止或提交，提交并达标后即可解锁";
            return;
        }
        if (gate.passed >= threshold * gate.required - EPSILON) {
            outcome.unlocked = true;
            outcome.earned = true;
            outcome.ratioAtUnlock = gate.ratio();
            return;
        }
        int need = Math.max(1, (int) Math.ceil(threshold * gate.required - EPSILON) - gate.passed);
        if (gate.pending > 0 && gate.pending >= need) {
            outcome.reasonText = "有 " + gate.pending + " 个" + gateName + "任务在等教师批改，达标后即可解锁";
        } else if (gate.pending > 0) {
            outcome.reasonText = gateName + "还差 " + need + " 个任务达标，其中 " + gate.pending + " 个在等教师批改";
        } else if (gate.redoWaiting > 0) {
            outcome.reasonText = gateName + "还差 " + need + " 个任务达标，有 " + gate.redoWaiting
                    + " 个已打回的任务可以重交";
        } else {
            outcome.reasonText = gateName + "还差 " + need + " 个任务达标（需达标 "
                    + Math.round(threshold * 100) + "%）";
        }
    }

    /** 学生本人的实际截止时间：任务截止时间与个别延期取较晚者，打回重做的记录沿用重做规则；为空表示不限 */
    static LocalDateTime effectiveDeadline(StudentLevelTaskRow row) {
        return HomeworkDeadlineUtils.getStudentEffectiveDeadline(row.getDeadline(), row.getRecordStatus(),
                row.getRedoDeadline(), row.getPersonalDeadline());
    }

    /** 单个任务是否计为达标时使用的分数；不计时返回 null */
    static Integer gradedScore(StudentLevelTaskRow row, boolean keepScoreDuringRedo) {
        Integer status = row.getRecordStatus();
        if (Objects.equals(status, EduHomeworkRecordStatusEnum.CORRECTED)) {
            return row.getRecordScore();
        }
        if (keepScoreDuringRedo && (Objects.equals(status, EduHomeworkRecordStatusEnum.REDO)
                || Objects.equals(status, EduHomeworkRecordStatusEnum.RESUBMITTED))) {
            return row.getLastCorrectScore() != null ? row.getLastCorrectScore() : row.getRecordScore();
        }
        return null;
    }

    private static final class LevelStats {
        int assigned;
        int required;
        int passed;
        int pending;
        int redoWaiting;

        void add(StudentLevelTaskRow row, boolean keepScoreDuringRedo, LocalDateTime now) {
            assigned++;
            Integer status = row.getRecordStatus();
            boolean submitted = status != null && status > EduHomeworkRecordStatusEnum.NOT_SUBMITTED;
            LocalDateTime deadline = effectiveDeadline(row);
            boolean due = deadline != null && deadline.isBefore(now);
            if (!submitted && !due) {
                return;
            }
            required++;
            Integer score = gradedScore(row, keepScoreDuringRedo);
            if (score != null && row.getPassScore() != null && score >= row.getPassScore()) {
                passed++;
            } else if (submitted && EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(status)) {
                pending++;
            } else if (Objects.equals(status, EduHomeworkRecordStatusEnum.REDO)) {
                redoWaiting++;
            }
        }

        Double ratio() {
            return required == 0 ? null : (double) passed / required;
        }
    }

    /** 单个等级的计算结果 */
    public static final class LevelOutcome {
        public final int level;
        public final int assignedCount;
        public final int requiredCount;
        public final int passedCount;
        public final int pendingReviewCount;
        public final int redoCount;
        /** 本等级的达标比例（0~1），分母为 0 时为空 */
        public final Double ratio;
        public final double threshold;
        boolean unlocked;
        /** 本次按规则解锁（没有快照），调用方据此写入快照 */
        boolean earned;
        /** 本次按插班生规则解锁（上一等级没有分配给学生的任务），快照不带达标比例 */
        boolean lateJoinerPass;
        Double ratioAtUnlock;
        LocalDateTime unlockedAt;
        String reasonText;

        LevelOutcome(int level, int assignedCount, int requiredCount, int passedCount, int pendingReviewCount,
                     int redoCount, Double ratio, double threshold) {
            this.level = level;
            this.assignedCount = assignedCount;
            this.requiredCount = requiredCount;
            this.passedCount = passedCount;
            this.pendingReviewCount = pendingReviewCount;
            this.redoCount = redoCount;
            this.ratio = ratio;
            this.threshold = threshold;
        }

        public boolean isUnlocked() {
            return unlocked;
        }

        public boolean isEarned() {
            return earned;
        }

        public boolean isLateJoinerPass() {
            return lateJoinerPass;
        }

        public Double getRatioAtUnlock() {
            return ratioAtUnlock;
        }

        public LocalDateTime getUnlockedAt() {
            return unlockedAt;
        }

        public String getReasonText() {
            return reasonText;
        }
    }
}
