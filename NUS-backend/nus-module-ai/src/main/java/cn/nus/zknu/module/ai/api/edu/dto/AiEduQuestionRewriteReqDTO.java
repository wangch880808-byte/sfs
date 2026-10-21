package cn.nus.zknu.module.ai.api.edu.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 题目改写请求 DTO
 */
@Data
public class AiEduQuestionRewriteReqDTO implements Serializable {

    /**
     * 原题标题
     */
    private String title;
    /**
     * 原题要求
     */
    private String requirement;
    /**
     * 课程名称
     */
    private String courseName;
    /**
     * 技能指标名称
     */
    private String indicatorName;
    /**
     * 任务等级
     */
    private Integer homeworkLevel;

}
