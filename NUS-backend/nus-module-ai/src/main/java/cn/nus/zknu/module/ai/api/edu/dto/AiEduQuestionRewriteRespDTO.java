package cn.nus.zknu.module.ai.api.edu.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 题目改写响应 DTO
 */
@Data
public class AiEduQuestionRewriteRespDTO implements Serializable {

    /**
     * 是否成功生成
     */
    private Boolean success;
    /**
     * 改写后的任务标题
     */
    private String title;
    /**
     * 改写后的任务要求
     */
    private String requirement;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 模型原始响应
     */
    private String rawResponse;

}
