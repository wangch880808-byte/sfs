package cn.nus.zknu.module.edu.service.student;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.student.EduStudentLevelUnlockDO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
import cn.nus.zknu.module.edu.dal.mysql.student.EduStudentLevelUnlockMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.edu.service.student.StudentLevelUnlockCalculator.LevelOutcome;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_LEVEL_LOCKED;

/**
 * 等级闯关：按 {@link StudentLevelUnlockCalculator} 的规则计算，并把第一次达标解锁写成快照（之后不再收回）。
 */
@Service
@Slf4j
public class StudentLevelUnlockService {

    /** 打回、重交期间是否沿用上一次批改的分数计入达标 */
    public static final String KEEP_SCORE_DURING_REDO_KEY = "edu.unlock.keep-score-during-redo";
    /** 插班生：所在班级发布过上一等级任务、但一个都没有分配给该学生时，是否视为通过 */
    public static final String LATE_JOINER_PASS_KEY = "edu.unlock.late-joiner-pass";

    @Resource
    private StudentCourseMapper studentCourseMapper;
    @Resource
    private EduStudentLevelUnlockMapper unlockMapper;
    @Resource
    private EduMetricPolicy metricPolicy;
    @Resource
    private ConfigApi configApi;

    /**
     * 计算各等级解锁状态。
     *
     * @param history true 表示课程已归档、只读查看：按历史可见口径统计，不写快照
     */
    public List<AppStudentUnlockStatusRespVO.Level> getLevels(Long studentId, Long courseId, boolean history) {
        LocalDateTime now = LocalDateTime.now();
        List<LevelOutcome> outcomes = evaluate(studentId, courseId, history, now);
        List<AppStudentUnlockStatusRespVO.Level> levels = new ArrayList<>(outcomes.size());
        for (LevelOutcome outcome : outcomes) {
            levels.add(toLevel(outcome));
        }
        return levels;
    }

    /**
     * 学生开始一个新任务前校验等级已解锁；没有解锁时提示原因。
     */
    public void validateLevelUnlocked(Long studentId, Long courseId, Integer level) {
        if (level == null || level <= 1 || level > StudentLevelUnlockCalculator.MAX_LEVEL) {
            return;
        }
        LevelOutcome outcome = evaluate(studentId, courseId, false, LocalDateTime.now()).get(level - 1);
        if (!outcome.isUnlocked()) {
            String reason = StrUtil.blankToDefault(outcome.getReasonText(), "请先完成上一等级任务");
            throw exception0(STUDENT_COURSE_LEVEL_LOCKED.getCode(), "{}还没有解锁：{}",
                    StudentLevelUnlockCalculator.levelName(level), reason);
        }
    }

    public boolean keepScoreDuringRedo() {
        return readSwitch(KEEP_SCORE_DURING_REDO_KEY, true);
    }

    public boolean lateJoinerPass() {
        return readSwitch(LATE_JOINER_PASS_KEY, true);
    }

    /** 读取 true/false/1/0 开关；为空、无效或读取失败时用默认值 */
    private boolean readSwitch(String key, boolean defaultValue) {
        try {
            String value = configApi.getConfigValueByKey(key);
            if (StrUtil.isBlank(value)) {
                return defaultValue;
            }
            String normalized = value.trim().toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
            log.warn("等级闯关配置无效，使用默认值 {}：{}={}", defaultValue, key, value);
            return defaultValue;
        } catch (Exception ex) {
            log.warn("读取等级闯关配置失败，使用默认值 {}：{}", defaultValue, key);
            return defaultValue;
        }
    }

    private List<LevelOutcome> evaluate(Long studentId, Long courseId, boolean history, LocalDateTime now) {
        List<StudentLevelTaskRow> rows = studentCourseMapper.selectLevelTasks(studentId, courseId, history);
        for (StudentLevelTaskRow row : rows) {
            if (row.getPassScore() == null) {
                row.setPassScore(metricPolicy.defaultPassScore(row.getFullScore()));
            }
        }
        Map<Integer, Integer> classLevelCounts = new HashMap<>();
        if (!history) {
            for (Map<String, Object> item : studentCourseMapper.selectClassLevelTaskCounts(studentId, courseId)) {
                classLevelCounts.put(((Number) item.get("homeworkLevel")).intValue(),
                        ((Number) item.get("taskCount")).intValue());
            }
        }
        Map<Integer, EduStudentLevelUnlockDO> snapshots = unlockMapper.selectListByStudentAndCourse(studentId, courseId)
                .stream().collect(Collectors.toMap(EduStudentLevelUnlockDO::getLevel, Function.identity(),
                        (left, right) -> left));
        // 往期课程只读查看时没有班级任务数，插班生规则不会触发
        List<LevelOutcome> outcomes = StudentLevelUnlockCalculator.calculate(rows, classLevelCounts, snapshots,
                metricPolicy.unlockRatio(), keepScoreDuringRedo(), !history && lateJoinerPass(), now);
        if (!history) {
            for (LevelOutcome outcome : outcomes) {
                if (outcome.isEarned()) {
                    saveSnapshot(studentId, courseId, outcome, now);
                }
            }
        }
        return outcomes;
    }

    private void saveSnapshot(Long studentId, Long courseId, LevelOutcome outcome, LocalDateTime now) {
        BigDecimal ratio = outcome.getRatioAtUnlock() == null ? null
                : BigDecimal.valueOf(outcome.getRatioAtUnlock()).setScale(4, RoundingMode.HALF_UP);
        try {
            unlockMapper.insertIfAbsent(studentId, courseId, outcome.level, now, ratio, String.valueOf(studentId));
            outcome.unlockedAt = now;
            if (outcome.isLateJoinerPass()) {
                log.info("[saveSnapshot][插班生没有分配到上一等级任务，按配置放行 studentId={}, courseId={}, level={}]",
                        studentId, courseId, outcome.level);
            }
        } catch (Exception ex) {
            // 快照只是“不再收回”的保障，写入失败不影响本次按规则得出的解锁结果
            log.warn("[saveSnapshot][写入等级解锁快照失败 studentId={}, courseId={}, level={}]",
                    studentId, courseId, outcome.level, ex);
        }
    }

    private AppStudentUnlockStatusRespVO.Level toLevel(LevelOutcome outcome) {
        AppStudentUnlockStatusRespVO.Level item = new AppStudentUnlockStatusRespVO.Level();
        item.setLevel(outcome.level);
        item.setLevelName(StudentLevelUnlockCalculator.levelName(outcome.level));
        item.setTotalTasks(outcome.assignedCount);
        item.setQualifiedTasks(outcome.passedCount);
        item.setQualificationRate(outcome.ratio == null ? 0D
                : BigDecimal.valueOf(outcome.ratio * 100).setScale(1, RoundingMode.HALF_UP).doubleValue());
        item.setUnlocked(outcome.isUnlocked());
        item.setUnlockedAt(outcome.getUnlockedAt());
        item.setPassedCount(outcome.passedCount);
        item.setRequiredCount(outcome.requiredCount);
        item.setPendingReviewCount(outcome.pendingReviewCount);
        item.setRatio(outcome.ratio == null ? null
                : BigDecimal.valueOf(outcome.ratio).setScale(4, RoundingMode.HALF_UP).doubleValue());
        item.setThreshold(outcome.threshold);
        item.setReasonText(outcome.isUnlocked() ? null : outcome.getReasonText());
        return item;
    }
}
