package cn.nus.zknu.module.edu.service.aireview.support;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StudentVisibleCommentFormatter {

    private static final Pattern QUOTED_TEXT_PATTERN = Pattern.compile("[“\"]([^”\"]+)[”\"]");

    public String format(EduHomeworkDO homework, Integer score, String draftComment) {
        if (StrUtil.isBlank(draftComment)) {
            return "";
        }
        String submissionMode = homework != null
                ? StrUtil.blankToDefault(homework.getSubmissionMode(), "text")
                : "text";
        String normalized = sanitizeDraftComment(draftComment);
        if (StrUtil.isBlank(normalized)) {
            return defaultCommentByMode(submissionMode, score);
        }
        if ("audio".equalsIgnoreCase(submissionMode)) {
            return formatAudioComment(normalized, score);
        }
        if ("image".equalsIgnoreCase(submissionMode)) {
            return formatImageComment(normalized, score);
        }
        return formatGenericComment(normalized, score);
    }

    private String formatAudioComment(String normalized, Integer score) {
        boolean verified = containsAny(normalized,
                "基本一致", "能够完整读出", "完整读出", "已完成朗读任务", "完成朗读任务");
        if (!verified) {
            return "你已提交本次朗读任务，但基于当前提交内容，暂时还无法充分判断任务完成情况。"
                    + "建议补充更清晰、更完整的朗读内容，便于教师进一步复核。";
        }
        String quotedText = extractQuotedText(normalized);
        StringBuilder builder = new StringBuilder("你本次朗读内容与任务要求基本一致");
        if (StrUtil.isNotBlank(quotedText)) {
            builder.append("，能够完整读出「").append(quotedText).append("」");
        }
        builder.append("。");
        builder.append(buildEncouragement(score,
                "整体完成情况较好，建议继续保持语音清晰度和表达稳定性。",
                "整体完成情况不错，建议继续加强朗读流畅度和表达稳定性。",
                "已基本完成本次朗读任务，建议继续加强朗读准确度和表达连贯性。"));
        return builder.toString();
    }

    private String formatImageComment(String normalized, Integer score) {
        boolean verified = containsAny(normalized,
                "基本一致", "完成任务", "已完成", "内容完整", "符合要求");
        if (!verified) {
            return "你已提交本次任务，但基于当前提交内容，暂时还无法充分判断完成情况。"
                    + "建议补充更清晰、更完整的图片内容，便于教师进一步复核。";
        }
        StringBuilder builder = new StringBuilder("你已按要求完成本次任务");
        if (containsAny(normalized, "基本一致", "符合要求")) {
            builder.append("，内容与任务要求基本一致");
        }
        builder.append("。");
        builder.append(buildEncouragement(score,
                "整体完成情况较好，建议继续保持内容清晰度和版面规范性。",
                "整体完成情况不错，建议继续加强书写清晰度和版面整洁度。",
                "已基本完成本次任务，建议继续加强内容完整性和呈现规范性。"));
        return builder.toString();
    }

    private String formatGenericComment(String normalized, Integer score) {
        if (containsAny(normalized, "无法验证", "无法确认", "证据不足", "不充分判断")) {
            return "你已提交本次任务，但基于当前提交内容，暂时还无法充分判断任务完成情况。"
                    + "建议结合任务要求继续补充更完整的提交内容，便于教师进一步复核。";
        }
        return "你已基本完成本次任务要求。"
                + buildEncouragement(score,
                "整体完成情况较好，建议继续保持，并根据教师后续反馈进一步优化。",
                "整体完成情况不错，建议继续巩固本次任务中的关键要点。",
                "已基本完成本次任务，建议根据教师后续反馈继续完善。");
    }

    private String sanitizeDraftComment(String comment) {
        String normalized = StrUtil.trim(comment);
        String[] uselessPhrases = {
                "本评语基于现有作业内容形成，",
                "本评语基于现有作业内容形成",
                "不作为最终成绩依据，仅供教师结合原始音频人工复核参考。",
                "不作为最终成绩依据，仅供教师人工复核参考。",
                "不作为最终成绩依据，仅供教师复核参考。",
                "仅供教师结合原始音频人工复核参考。",
                "仅供教师人工复核参考。",
                "仅供教师复核参考。",
                "仅依据当前作业证据，",
                "依据音频转写文本可识别出",
                "依据图片识别结果可判断出",
                "当前评分不作为最终成绩依据，",
                "本次结果仅供教师人工复核参考。"
        };
        for (String phrase : uselessPhrases) {
            normalized = normalized.replace(phrase, "");
        }
        normalized = normalized.replace("学生", "你");
        normalized = normalized.replace("能够完整读出", "能够完整读出");
        normalized = normalized.replace("判断为已完成朗读任务", "已完成本次朗读任务");
        normalized = normalized.replace("整体完成度较好", "整体完成情况较好");
        normalized = normalized.replace("整体完成度不错", "整体完成情况不错");
        normalized = normalized.replaceAll("，暂不对[^。]*作更高评价。?", "。");
        normalized = normalized.replaceAll("，不作为最终成绩依据[^。]*。?", "。");
        normalized = normalized.replaceAll("，仅供教师[^。]*。?", "。");
        normalized = normalized.replaceAll("。{2,}", "。");
        normalized = normalized.replaceAll("\\s+", "");
        return StrUtil.trim(normalized);
    }

    private String defaultCommentByMode(String submissionMode, Integer score) {
        if ("audio".equalsIgnoreCase(submissionMode)) {
            return "你已提交本次朗读任务。"
                    + buildEncouragement(score,
                    "整体完成情况较好，建议继续保持语音清晰度和表达稳定性。",
                    "整体完成情况不错，建议继续加强朗读流畅度和表达稳定性。",
                    "已基本完成本次朗读任务，建议继续加强朗读准确度和表达连贯性。");
        }
        if ("image".equalsIgnoreCase(submissionMode)) {
            return "你已提交本次任务。"
                    + buildEncouragement(score,
                    "整体完成情况较好，建议继续保持内容清晰度和版面规范性。",
                    "整体完成情况不错，建议继续加强书写清晰度和版面整洁度。",
                    "已基本完成本次任务，建议继续加强内容完整性和呈现规范性。");
        }
        return "你已完成本次提交，建议结合教师后续反馈继续完善。";
    }

    private String buildEncouragement(
            Integer score, String highScoreText, String mediumScoreText, String lowScoreText) {
        int finalScore = score == null ? 0 : score;
        if (finalScore >= 85) {
            return highScoreText;
        }
        if (finalScore >= 70) {
            return mediumScoreText;
        }
        return lowScoreText;
    }

    private String extractQuotedText(String value) {
        Matcher matcher = QUOTED_TEXT_PATTERN.matcher(StrUtil.blankToDefault(value, ""));
        if (matcher.find()) {
            return StrUtil.trim(matcher.group(1));
        }
        return null;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (StrUtil.contains(value, keyword)) {
                return true;
            }
        }
        return false;
    }
}
