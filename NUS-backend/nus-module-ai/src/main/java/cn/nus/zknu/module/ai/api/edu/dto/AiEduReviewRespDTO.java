package cn.nus.zknu.module.ai.api.edu.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI教育评阅响应 DTO
 */
@Data
public class AiEduReviewRespDTO implements Serializable {

    private Long modelId;
    private String modelName;

    /**
     * AI评分(0-100)
     */
    private Integer score;
    /**
     * AI评语
     */
    private String comment;
    /**
     * 置信度
     */
    private Double confidence;
    /**
     * 分项得分(JSON)
     */
    private String subScoresJson;
    /**
     * 风险标记(JSON)
     */
    private String riskFlagsJson;
    /**
     * 结构化证据摘要(JSON)
     */
    private String evidenceSummaryJson;
    /**
     * 模型原始响应
     */
    private String rawResponse;

}
