package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiCalibrationSample;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiCalibrationSampleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 教师评分偏好只使用聚合统计，不向模型发送历史学生信息。 */
@Service
public class AiReviewCalibrationService {

    private static final int MIN_SAMPLES = 5;
    private static final int WINDOW_DAYS = 180;
    private static final double DEVIATION_THRESHOLD = 0.03;
    private static final int MIN_DIRECTION_FEEDBACK = 3;

    @Resource
    private AiCalibrationSampleMapper sampleMapper;

    public AiReviewCalibrationRespVO calculate(Long teacherId, EduHomeworkDO homework) {
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<AiCalibrationSample> samples = sampleMapper.selectRecent(teacherId, homework.getCourseId(),
                homework.getId(), true, since);
        String scope = "TASK";
        if (samples.size() < MIN_SAMPLES) {
            samples = sampleMapper.selectRecent(teacherId, homework.getCourseId(), homework.getId(), false, since);
            scope = "COURSE";
        }
        AiReviewCalibrationRespVO result = summarize(samples, scope, homework.getScore());
        return result;
    }

    static AiReviewCalibrationRespVO summarize(List<AiCalibrationSample> samples, String scope, Integer fullScoreValue) {
        AiReviewCalibrationRespVO result = new AiReviewCalibrationRespVO();
        int fullScore = fullScoreValue != null && fullScoreValue > 0 ? fullScoreValue : 100;
        result.setFullScore(fullScore);
        result.setSampleCount(samples.size());
        result.setScope(samples.size() >= MIN_SAMPLES ? scope : "NONE");
        int deviations = 0;
        double deviationSum = 0;
        double percentSum = 0;
        for (AiCalibrationSample sample : samples) {
            if ("strict".equals(sample.getFeedbackType())) result.setStrictCount(result.getStrictCount() + 1);
            if ("normal".equals(sample.getFeedbackType())) result.setNormalCount(result.getNormalCount() + 1);
            if ("loose".equals(sample.getFeedbackType())) result.setLooseCount(result.getLooseCount() + 1);
            if (sample.getDeviation() != null) {
                int sampleFullScore = sample.getFullScore() != null && sample.getFullScore() > 0
                        ? sample.getFullScore() : 100;
                deviations++;
                deviationSum += sample.getDeviation();
                percentSum += (double) sample.getDeviation() / sampleFullScore;
            }
        }
        result.setAverageDeviation(deviations == 0 ? 0 : Math.round(deviationSum * 100.0 / deviations) / 100.0);
        if (samples.size() < MIN_SAMPLES) {
            result.setDirection("INSUFFICIENT");
            return result;
        }
        double averagePercent = deviations == 0 ? 0 : percentSum / deviations;
        if (deviations > 0 && averagePercent <= -DEVIATION_THRESHOLD) {
            result.setDirection("STRICTER");
        } else if (deviations > 0 && averagePercent >= DEVIATION_THRESHOLD) {
            result.setDirection("LOOSER");
        } else if (result.getStrictCount() >= MIN_DIRECTION_FEEDBACK
                && result.getStrictCount() >= result.getLooseCount() * 2) {
            result.setDirection("STRICTER");
        } else if (result.getLooseCount() >= MIN_DIRECTION_FEEDBACK
                && result.getLooseCount() >= result.getStrictCount() * 2) {
            result.setDirection("LOOSER");
        } else {
            result.setDirection("NONE");
        }
        if (!"NONE".equals(result.getDirection())) {
            String preference = "STRICTER".equals(result.getDirection()) ? "从严" : "从宽";
            result.setHint(String.format("教师评分偏好：%s范围最近%d条样本，教师终评分与AI草案平均偏差为%.2f分，当前任务满分%d分；加严反馈%d次，放宽反馈%d次。在严格依据评分标准的前提下适当%s把握，不得因此偏离评分标准或编造依据。",
                    "TASK".equals(scope) ? "当前任务" : "同一课程", samples.size(),
                    result.getAverageDeviation(), fullScore, result.getStrictCount(), result.getLooseCount(), preference));
        }
        return result;
    }
}
