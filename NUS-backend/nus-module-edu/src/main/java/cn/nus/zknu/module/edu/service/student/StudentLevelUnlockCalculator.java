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
 * 等级闯关统计（纯计算，不读写数据库，便于单测）。
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
 * <h3>等级开放口径</h3>
 * 初级、中级、高级始终全部开放：各等级任务可以直接开始，达标数据只用于展示进度，不再作为下一等级的门槛。
 * 方法签名保留原有的统计输入参数，是为了让调用方和测试不用关心哪些参数只影响展示口径。
 */
public final class StudentLevelUnlockCalculator {

    public static final int MAX_LEVEL = 3;
    static final String[] LEVEL_NAMES = {"初级", "中级", "高级"};

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
        for (int level = 1; level <= MAX_LEVEL; level++) {
            LevelStats own = stats[level];
            LevelOutcome outcome = new LevelOutcome(level, own.assigned, own.required, own.passed, own.pending,
                    own.redoWaiting, own.ratio(), threshold);
            outcome.unlocked = true;
            outcomes.add(outcome);
        }
        return outcomes;
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

        public Double getRatio() {
            return ratio;
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
