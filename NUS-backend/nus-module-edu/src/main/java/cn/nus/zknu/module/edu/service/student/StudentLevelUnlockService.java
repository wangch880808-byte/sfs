package cn.nus.zknu.module.edu.service.student;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentLevelTaskRow;
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
import java.util.List;

/**
 * 等级闯关：按 {@link StudentLevelUnlockCalculator} 的规则统计各等级进度。
 * 等级门槛已取消，初级、中级、高级始终开放；本服务不再写解锁快照，也不再拦截任务开始。
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
     * 学生开始新任务前的等级校验。等级门槛已取消，各等级始终开放，这里只保留入口以兼容现有调用链。
     */
    public void validateLevelUnlocked(Long studentId, Long courseId, Integer level) {
        // 等级不再作为任务开始的门槛
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
        // 等级门槛取消后，班级任务数与解锁快照都不再参与判定；保留 history 参数仅供只读口径查询任务行
        return StudentLevelUnlockCalculator.calculate(rows, java.util.Map.of(), java.util.Map.of(),
                metricPolicy.unlockRatio(), keepScoreDuringRedo(), false, now);
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
