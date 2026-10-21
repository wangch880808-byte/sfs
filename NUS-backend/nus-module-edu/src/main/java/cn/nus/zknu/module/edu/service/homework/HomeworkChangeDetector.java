package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 已发布任务保存后，找出学生需要知道的关键变更：截止时间、任务内容、附件要求、提交方式、满分、及格线。
 * 其余字段（名称、指标、AI 配置等）的改动不通知学生。
 */
public final class HomeworkChangeDetector {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, String> SUBMISSION_MODE_LABELS = Map.of(
            "text", "纯文本",
            "image", "图片",
            "audio", "音频",
            "video", "视频",
            "mixed", "混合");

    private HomeworkChangeDetector() {
    }

    /**
     * @return 给学生看的变更说明，按固定顺序排列；没有关键变更时为空列表
     */
    public static List<String> describeKeyChanges(EduHomeworkDO before, EduHomeworkDO after) {
        List<String> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }
        if (!sameMinute(before.getDeadline(), after.getDeadline())) {
            changes.add(after.getDeadline() == null ? "取消了截止时间"
                    : "截止时间改为 " + after.getDeadline().format(TIME_FORMAT));
        }
        if (!sameText(before.getRequirement(), after.getRequirement())) {
            changes.add("任务内容已修改");
        }
        if (!sameCompact(before.getRequiredAttachmentTypesJson(), after.getRequiredAttachmentTypesJson())) {
            changes.add("附件要求已修改");
        }
        if (!sameText(before.getSubmissionMode(), after.getSubmissionMode())) {
            changes.add("提交方式改为" + SUBMISSION_MODE_LABELS.getOrDefault(
                    StrUtil.trim(after.getSubmissionMode()), "其他方式"));
        }
        if (!Objects.equals(before.getScore(), after.getScore())) {
            changes.add("满分改为 " + after.getScore() + " 分");
        }
        if (!Objects.equals(before.getPassScore(), after.getPassScore())) {
            changes.add("及格线改为 " + after.getPassScore() + " 分");
        }
        return changes;
    }

    /** 把变更说明拼成一句，用于通知正文 */
    public static String join(List<String> changes) {
        return String.join("；", changes);
    }

    private static boolean sameMinute(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.withSecond(0).withNano(0).equals(right.withSecond(0).withNano(0));
    }

    private static boolean sameText(String left, String right) {
        return StrUtil.equals(StrUtil.trimToEmpty(left), StrUtil.trimToEmpty(right));
    }

    /** 忽略空白差异（JSON 序列化格式不同不算变更） */
    private static boolean sameCompact(String left, String right) {
        return StrUtil.equals(StrUtil.cleanBlank(StrUtil.nullToEmpty(left)),
                StrUtil.cleanBlank(StrUtil.nullToEmpty(right)));
    }

}
