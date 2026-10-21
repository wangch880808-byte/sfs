package cn.nus.zknu.module.ai.enums.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum AiBusinessCodeEnum {

    HOMEWORK_REWRITE("HOMEWORK_REWRITE", "任务题目改写"),
    HOMEWORK_REVIEW("HOMEWORK_REVIEW", "任务 AI 初评"),
    CLASS_ANALYSIS("CLASS_ANALYSIS", "班级学情分析"),
    STUDENT_ANALYSIS("STUDENT_ANALYSIS", "学生分析与评语"),
    EDU_CHAT("EDU_CHAT", "教育业务对话");

    private final String code;
    private final String name;

    public static AiBusinessCodeEnum validate(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("非法 AI 业务码：" + code));
    }

}
