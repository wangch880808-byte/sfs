package cn.nus.zknu.module.edu.service.homework;

import java.util.Objects;

/**
 * 批改方式只有两种：教师手动批改（autoGrade=0）与 AI 初评 + 教师复核（autoGrade=1）。
 * 原「AI 自动批改」（autoGrade=1、aiReviewMode=0）从未真正生效，保存时一律归一化为 AI 初评。
 */
public final class HomeworkGradingMode {

    private HomeworkGradingMode() {
    }

    /**
     * 按 autoGrade 推出应保存的 aiReviewMode。
     *
     * @param autoGrade    本次保存后生效的 autoGrade（更新时请求未带则取库里的值）
     * @param aiReviewMode 请求里的 aiReviewMode
     * @return autoGrade=1 时为 1；autoGrade=0 时为 0；autoGrade 未知时原样返回
     */
    public static Integer normalizeAiReviewMode(Integer autoGrade, Integer aiReviewMode) {
        if (Objects.equals(autoGrade, 1)) {
            return 1;
        }
        if (Objects.equals(autoGrade, 0)) {
            return 0;
        }
        return aiReviewMode;
    }
}
