package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 统计口径的唯一入口。配置分数线按百分制存储，任务满分非 100 时按比例换算。 */
@Service
@Slf4j
public class EduMetricPolicy {

    @Resource
    private ConfigApi configApi;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;

    public long expectedStudents(Long homeworkId) {
        return assigneeMapper.selectCount(EduHomeworkAssigneeDO::getHomeworkId, homeworkId);
    }

    public int passScore(EduHomeworkDO homework) {
        if (homework.getPassScore() != null) {
            return homework.getPassScore();
        }
        return defaultPassScore(homework.getScore());
    }

    public int defaultPassScore(Integer fullScore) {
        return passScoreCalculator().defaultPassScore(fullScore);
    }

    /** 批量统计使用同一份配置快照，显式分数线的任务无需读取全局参数。 */
    public Map<Long, Integer> passScores(Collection<EduHomeworkDO> homeworks) {
        PassScoreCalculator calculator = homeworks.stream().anyMatch(homework -> homework.getPassScore() == null)
                ? passScoreCalculator() : new PassScoreCalculator(60);
        Map<Long, Integer> scores = new LinkedHashMap<>();
        homeworks.forEach(homework -> scores.put(homework.getId(), calculator.passScore(homework)));
        return scores;
    }

    private PassScoreCalculator passScoreCalculator() {
        double configured = readNumber("edu.pass.score", 60);
        if (!Double.isFinite(configured) || configured < 0 || configured > 100) {
            log.warn("达标分数配置无效，回退至 60：{}", configured);
            configured = 60;
        }
        return new PassScoreCalculator(configured);
    }

    private record PassScoreCalculator(double configured) {
        int defaultPassScore(Integer fullScore) {
            return (int) Math.round((fullScore == null ? 100 : fullScore) * configured / 100);
        }

        int passScore(EduHomeworkDO homework) {
            return homework.getPassScore() == null ? defaultPassScore(homework.getScore()) : homework.getPassScore();
        }
    }

    public double certificateThreshold() {
        double value = readNumber("edu.certificate.threshold", 80);
        return Double.isFinite(value) && value >= 0 && value <= 100 ? value : 80;
    }

    public double unlockRatio() {
        double value = readNumber("edu.course.unlock-ratio", 0.8);
        if (!Double.isFinite(value) || value <= 0 || value > 1) {
            log.warn("解锁比例配置无效，回退至 0.8：{}", value);
            return 0.8;
        }
        return value;
    }

    public ScoreRatios scoreRatios() {
        double good = readNumber("edu.score.good-ratio", 0.75);
        double excellent = readNumber("edu.score.excellent-ratio", 0.85);
        if (!Double.isFinite(good) || !Double.isFinite(excellent)
                || good <= 0 || excellent <= 0 || good >= excellent || excellent > 1) {
            log.warn("成绩分档比例配置无效，回退至 0.75/0.85：{}/{}", good, excellent);
            return new ScoreRatios(0.75, 0.85);
        }
        return new ScoreRatios(good, excellent);
    }

    public ScoreBand scoreBand(double score, int passScore, int fullScore) {
        return scoreBand(score, passScore, fullScore, scoreRatios());
    }

    /** 批量统计复用比例快照，分档边界仍由同一算法计算。 */
    public ScoreBand scoreBand(double score, int passScore, Integer fullScore, ScoreRatios ratios) {
        int effectiveFullScore = fullScore == null ? 100 : fullScore;
        double good = Math.max(passScore, effectiveFullScore * ratios.good());
        double excellent = Math.max(good, effectiveFullScore * ratios.excellent());
        if (score < passScore) return ScoreBand.UNQUALIFIED;
        if (score < good) return ScoreBand.QUALIFIED;
        if (score < excellent) return ScoreBand.GOOD;
        return ScoreBand.EXCELLENT;
    }

    /** 已按成绩降序排序；相同分数使用竞赛名次。 */
    public int[] competitionRanks(List<Integer> descendingScores) {
        int[] ranks = new int[descendingScores.size()];
        for (int i = 0; i < ranks.length; i++) {
            ranks[i] = i > 0 && java.util.Objects.equals(descendingScores.get(i), descendingScores.get(i - 1))
                    ? ranks[i - 1] : i + 1;
        }
        return ranks;
    }

    /** 任一权重缺失或总权重为零时退化为等权平均，避免只计算部分指标。 */
    public Double weightedRate(Map<Long, Double> rates, Map<Long, Double> weights) {
        if (rates.isEmpty()) return null;
        boolean fallback = rates.keySet().stream().anyMatch(id -> weights.get(id) == null);
        double totalWeight = rates.keySet().stream().mapToDouble(id -> weights.getOrDefault(id, 0D)).sum();
        if (fallback || totalWeight <= 0) return rates.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return rates.entrySet().stream().mapToDouble(e -> e.getValue() * weights.get(e.getKey())).sum() / totalWeight;
    }

    public boolean unlocked(int level, Map<Integer, Double> previousLevelRates) {
        for (int previous = 1; previous < level; previous++) {
            // 没有指派任务的前置等级不会出现在 map 中，不阻挡后续等级。
            Double rate = previousLevelRates.get(previous);
            if (rate != null && rate < unlockRatio() * 100) return false;
        }
        return true;
    }

    private double readNumber(String key, double fallback) {
        try {
            String value = configApi.getConfigValueByKey(key);
            return value == null ? fallback : Double.parseDouble(value);
        } catch (Exception ex) {
            log.warn("读取统计配置失败，使用默认值：{}", key);
            return fallback;
        }
    }

    public record ScoreRatios(double good, double excellent) { }
    public enum ScoreBand { UNQUALIFIED, QUALIFIED, GOOD, EXCELLENT }
}
