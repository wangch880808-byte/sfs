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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_LEVEL_LOCKED;

/**
 * 等级闯关：按 {@link StudentLevelUnlockCalculator} 的规则计算，并把第一次达标解锁写成快照（之后不再收回）。
 * <p>
 * 闯关的计算单位是“课程 + 一级指标（技能）”：同一门课里书写、表达等技能各自独立闯关，互不影响。
 * 任务、班级任务数、快照都按 indicatorFirstId 分组，每组各调用一次计算器；没填指标的任务归到 0 组，
 * 沿用原来整门课的口径。返回给前端的是各组等级的平铺列表，等级名与未解锁原因前带技能名。
 */
@Service
@Slf4j
public class StudentLevelUnlockService {

    /** 打回、重交期间是否沿用上一次批改的分数计入达标 */
    public static final String KEEP_SCORE_DURING_REDO_KEY = "edu.unlock.keep-score-during-redo";
    /** 插班生：所在班级发布过上一等级任务、但一个都没有分配给该学生时，是否视为通过 */
    public static final String LATE_JOINER_PASS_KEY = "edu.unlock.late-joiner-pass";
    /** 没填一级指标的任务归到这一组 */
    private static final long NO_INDICATOR = 0L;

    @Resource
    private StudentCourseMapper studentCourseMapper;
    @Resource
    private EduStudentLevelUnlockMapper unlockMapper;
    @Resource
    private EduMetricPolicy metricPolicy;
    @Resource
    private ConfigApi configApi;

    /**
     * 计算各等级解锁状态（按一级指标分组后平铺）。
     *
     * @param history true 表示课程已归档、只读查看：按历史可见口径统计，不写快照
     */
    public List<AppStudentUnlockStatusRespVO.Level> getLevels(Long studentId, Long courseId, boolean history) {
        List<SkillOutcome> groups = evaluate(studentId, courseId, history, LocalDateTime.now());
        List<AppStudentUnlockStatusRespVO.Level> levels = new ArrayList<>();
        for (SkillOutcome group : groups) {
            for (LevelOutcome outcome : group.levels) {
                levels.add(toLevel(outcome, group.indicatorFirstId, group.indicatorName));
            }
        }
        return levels;
    }

    /**
     * 学生开始一个新任务前校验等级已解锁；只看任务所属的一级指标（技能）那一组。
     * 没有解锁时提示原因。indicatorFirstId 为空按 0 组处理。
     */
    public void validateLevelUnlocked(Long studentId, Long courseId, Integer level, Long indicatorFirstId) {
        if (level == null || level <= 1 || level > StudentLevelUnlockCalculator.MAX_LEVEL) {
            return;
        }
        long indicatorId = indicatorFirstId == null ? NO_INDICATOR : indicatorFirstId;
        List<SkillOutcome> groups = evaluate(studentId, courseId, false, LocalDateTime.now());
        SkillOutcome group = groups.stream()
                .filter(item -> item.indicatorFirstId == indicatorId)
                .findFirst().orElse(null);
        if (group == null) {
            // 这个技能还没有任何任务/快照，按“等级不存在直接放行”处理
            return;
        }
        LevelOutcome outcome = group.levels.get(level - 1);
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

    /** 按一级指标分组计算；每组独立调用一次计算器 */
    private List<SkillOutcome> evaluate(Long studentId, Long courseId, boolean history, LocalDateTime now) {
        List<StudentLevelTaskRow> rows = studentCourseMapper.selectLevelTasks(studentId, courseId, history);
        for (StudentLevelTaskRow row : rows) {
            if (row.getPassScore() == null) {
                row.setPassScore(metricPolicy.defaultPassScore(row.getFullScore()));
            }
        }

        // 任务、班级任务数、快照按指标分组
        Map<Long, List<StudentLevelTaskRow>> rowsByIndicator = new LinkedHashMap<>();
        Map<Long, String> indicatorNames = new HashMap<>();
        for (StudentLevelTaskRow row : rows) {
            long indicatorId = row.getIndicatorFirstId() == null ? NO_INDICATOR : row.getIndicatorFirstId();
            rowsByIndicator.computeIfAbsent(indicatorId, key -> new ArrayList<>()).add(row);
            if (indicatorId != NO_INDICATOR && StrUtil.isNotBlank(row.getIndicatorName())) {
                indicatorNames.putIfAbsent(indicatorId, row.getIndicatorName());
            }
        }

        Map<Long, Map<Integer, Integer>> classCountsByIndicator = new HashMap<>();
        if (!history) {
            for (Map<String, Object> item : studentCourseMapper.selectClassLevelTaskCounts(studentId, courseId)) {
                Object rawIndicator = item.get("indicatorFirstId");
                long indicatorId = rawIndicator == null ? NO_INDICATOR : ((Number) rawIndicator).longValue();
                int level = ((Number) item.get("homeworkLevel")).intValue();
                int count = ((Number) item.get("taskCount")).intValue();
                classCountsByIndicator.computeIfAbsent(indicatorId, key -> new HashMap<>()).put(level, count);
            }
        }

        Map<Long, Map<Integer, EduStudentLevelUnlockDO>> snapshotsByIndicator = new HashMap<>();
        for (EduStudentLevelUnlockDO snapshot : unlockMapper.selectListByStudentAndCourse(studentId, courseId)) {
            long indicatorId = snapshot.getIndicatorFirstId() == null ? NO_INDICATOR : snapshot.getIndicatorFirstId();
            snapshotsByIndicator.computeIfAbsent(indicatorId, key -> new HashMap<>())
                    .put(snapshot.getLevel(), snapshot);
        }

        // 所有出现过的指标都要算：任务、班级任务数、快照的并集。
        TreeSet<Long> indicatorIds = new TreeSet<>();
        indicatorIds.addAll(rowsByIndicator.keySet());
        indicatorIds.addAll(classCountsByIndicator.keySet());
        indicatorIds.addAll(snapshotsByIndicator.keySet());
        if (indicatorIds.isEmpty()) {
            // 学生暂时没有任何任务：仍按 0 组返回三个等级，保持页面有等级闯关栏
            indicatorIds.add(NO_INDICATOR);
        }

        List<SkillOutcome> result = new ArrayList<>(indicatorIds.size());
        for (Long indicatorId : indicatorIds) {
            List<LevelOutcome> outcomes = StudentLevelUnlockCalculator.calculate(
                    rowsByIndicator.getOrDefault(indicatorId, List.of()),
                    classCountsByIndicator.getOrDefault(indicatorId, Map.of()),
                    snapshotsByIndicator.getOrDefault(indicatorId, Map.of()),
                    metricPolicy.unlockRatio(), keepScoreDuringRedo(), !history && lateJoinerPass(), now);
            if (!history) {
                for (LevelOutcome outcome : outcomes) {
                    if (outcome.isEarned()) {
                        saveSnapshot(studentId, courseId, indicatorId, outcome, now);
                    }
                }
            }
            result.add(new SkillOutcome(indicatorId, indicatorNames.get(indicatorId), outcomes));
        }
        return result;
    }

    private void saveSnapshot(Long studentId, Long courseId, Long indicatorFirstId,
                              LevelOutcome outcome, LocalDateTime now) {
        BigDecimal ratio = outcome.getRatioAtUnlock() == null ? null
                : BigDecimal.valueOf(outcome.getRatioAtUnlock()).setScale(4, RoundingMode.HALF_UP);
        try {
            unlockMapper.insertIfAbsent(studentId, courseId, indicatorFirstId, outcome.level, now, ratio,
                    String.valueOf(studentId));
            outcome.unlockedAt = now;
            if (outcome.isLateJoinerPass()) {
                log.info("[saveSnapshot][插班生没有分配到上一等级任务，按配置放行 studentId={}, courseId={}, "
                                + "indicatorFirstId={}, level={}]",
                        studentId, courseId, indicatorFirstId, outcome.level);
            }
        } catch (Exception ex) {
            // 快照只是“不再收回”的保障，写入失败不影响本次按规则得出的解锁结果
            log.warn("[saveSnapshot][写入等级解锁快照失败 studentId={}, courseId={}, indicatorFirstId={}, level={}]",
                    studentId, courseId, indicatorFirstId, outcome.level, ex);
        }
    }

    private AppStudentUnlockStatusRespVO.Level toLevel(LevelOutcome outcome, Long indicatorFirstId,
                                                        String indicatorName) {
        AppStudentUnlockStatusRespVO.Level item = new AppStudentUnlockStatusRespVO.Level();
        String skill = StrUtil.isBlank(indicatorName) ? "" : indicatorName;
        item.setLevel(outcome.level);
        item.setIndicatorFirstId(indicatorFirstId);
        item.setLevelName(StrUtil.isBlank(skill) ? StudentLevelUnlockCalculator.levelName(outcome.level)
                : skill + " · " + StudentLevelUnlockCalculator.levelName(outcome.level));
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
        String reason = outcome.isUnlocked() ? null : outcome.getReasonText();
        item.setReasonText(StrUtil.isBlank(skill) || StrUtil.isBlank(reason) ? reason : skill + reason);
        return item;
    }

    /** 一个一级指标（技能）的一组等级计算结果 */
    private static final class SkillOutcome {
        final Long indicatorFirstId;
        final String indicatorName;
        final List<LevelOutcome> levels;

        SkillOutcome(Long indicatorFirstId, String indicatorName, List<LevelOutcome> levels) {
            this.indicatorFirstId = indicatorFirstId;
            this.indicatorName = indicatorName;
            this.levels = levels;
        }
    }
}
