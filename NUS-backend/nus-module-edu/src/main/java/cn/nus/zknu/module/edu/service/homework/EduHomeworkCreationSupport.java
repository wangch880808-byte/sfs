package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/** 创建任务共用的命名、默认值和分数校验。 */
@Service
public class EduHomeworkCreationSupport {

    @Resource
    private EduHomeworkMapper eduHomeworkMapper;
    @Resource
    private EduMetricPolicy metricPolicy;

    /**
     * 重名处理：同班级课程下重名任务自动加后缀(1)(2)...
     */
    public String generateUniqueName(Long classId, Long courseId, String name) {
        List<EduHomeworkDO> existing = eduHomeworkMapper.selectList(
                new LambdaQueryWrapper<EduHomeworkDO>()
                        .eq(EduHomeworkDO::getClassId, classId)
                        .eq(EduHomeworkDO::getCourseId, courseId)
                        .likeRight(EduHomeworkDO::getHomeworkName, name));
        if (existing.isEmpty()) {
            return name;
        }
        // 检查是否有完全同名的
        boolean exactMatch = existing.stream()
                .anyMatch(h -> h.getHomeworkName().equals(name));
        if (!exactMatch) {
            return name;
        }
        // 找到最大后缀编号
        int maxSuffix = 0;
        for (EduHomeworkDO h : existing) {
            String n = h.getHomeworkName();
            if (n.equals(name)) {
                maxSuffix = Math.max(maxSuffix, 0);
            } else if (n.startsWith(name + "(") && n.endsWith(")")) {
                try {
                    int suffix = Integer.parseInt(n.substring(name.length() + 1, n.length() - 1));
                    maxSuffix = Math.max(maxSuffix, suffix);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return name + "(" + (maxSuffix + 1) + ")";
    }

    public void fillHomeworkDefaults(EduHomeworkDO homework) {
        if (homework.getAutoGrade() == null) {
            homework.setAutoGrade(0);
        }
        if (homework.getSubmissionMode() == null) {
            homework.setSubmissionMode("text");
        }
        if (homework.getAiReviewMode() == null) {
            homework.setAiReviewMode(0);
        }
        homework.setAiReviewMode(HomeworkGradingMode.normalizeAiReviewMode(
                homework.getAutoGrade(), homework.getAiReviewMode()));
        if (homework.getAiConfidenceThreshold() == null) {
            homework.setAiConfidenceThreshold(0.7D);
        }
        if (homework.getPassScore() == null) {
            homework.setPassScore(metricPolicy.defaultPassScore(homework.getScore()));
        }
        if (homework.getScore() == null) {
            homework.setScore(100);
        }
        if (homework.getStatus() == null) {
            homework.setStatus(EduHomeworkStatusEnum.PUBLISHED);
        }
        if (homework.getHomeworkLevel() == null) {
            homework.setHomeworkLevel(1);
        }
        if (homework.getRequiredAttachmentTypesJson() == null) {
            homework.setRequiredAttachmentTypesJson(buildRequiredAttachmentTypesJson(homework.getSubmissionMode()));
        }
        if (homework.getAiRubricJson() == null) {
            homework.setAiRubricJson("");
        }
        if (homework.getAiReferenceJson() == null) {
            homework.setAiReferenceJson("");
        }
    }

    public void validateScoreConfig(Integer passScore, Integer score) {
        if (score == null || score <= 0 || (passScore != null && (passScore < 0 || passScore > score))
                || (passScore == null && metricPolicy.defaultPassScore(score) > score)) {
            throw exception(HOMEWORK_SCORE_INVALID);
        }
        if (score != 100) {
            throw exception(HOMEWORK_SCORE_PERCENT_ONLY);
        }
    }

    public String buildRequiredAttachmentTypesJson(String submissionMode) {
        return switch (submissionMode) {
            case "image" -> "[\"image\"]";
            case "audio" -> "[\"audio\"]";
            case "video" -> "[\"video\"]";
            case "mixed" -> "[\"image\",\"audio\",\"video\"]";
            default -> "[]";
        };
    }

}
