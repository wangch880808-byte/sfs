package cn.nus.zknu.module.ai.enums.prompt;

import cn.nus.zknu.module.ai.enums.model.AiBusinessCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 可在后台调整的系统提示词。一个 AI 业务可以有多段提示词（学生分析与评语分两段）。
 * <p>
 * requiredFragments 是结果解析依赖的内容，保存新版本时必须保留。
 */
@Getter
@AllArgsConstructor
public enum AiPromptCodeEnum {

    HOMEWORK_REVIEW("HOMEWORK_REVIEW", "任务 AI 初评", AiBusinessCodeEnum.HOMEWORK_REVIEW,
            AiPromptDefaults.HOMEWORK_REVIEW,
            List.of("JSON", "score", "comment", "confidence", "[学生作答开始]", "[学生作答结束]")),
    HOMEWORK_REWRITE("HOMEWORK_REWRITE", "任务题目改写", AiBusinessCodeEnum.HOMEWORK_REWRITE,
            AiPromptDefaults.HOMEWORK_REWRITE, List.of("JSON", "title", "requirement")),
    CLASS_ANALYSIS("CLASS_ANALYSIS", "班级学情分析", AiBusinessCodeEnum.CLASS_ANALYSIS,
            AiPromptDefaults.CLASS_ANALYSIS, List.of()),
    STUDENT_ANALYSIS("STUDENT_ANALYSIS", "学生个人诊断", AiBusinessCodeEnum.STUDENT_ANALYSIS,
            AiPromptDefaults.STUDENT_ANALYSIS, List.of()),
    STUDENT_COMMENT("STUDENT_COMMENT", "学生综合评语", AiBusinessCodeEnum.STUDENT_ANALYSIS,
            AiPromptDefaults.STUDENT_COMMENT, List.of());

    private final String code;
    private final String name;
    private final AiBusinessCodeEnum business;
    private final String defaultContent;
    private final List<String> requiredFragments;

    public static Optional<AiPromptCodeEnum> of(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }

}
