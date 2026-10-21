package cn.nus.zknu.module.ai.api.edu.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI教育评阅请求 DTO
 */
@Data
public class AiEduReviewReqDTO implements Serializable {

    /**
     * 学生提交内容
     */
    private String content;
    /**
     * 提交模式：text/image/audio/video/mixed
     */
    private String submissionMode;
    /**
     * 任务要求
     */
    private String requirement;
    /**
     * 评价指标名称
     */
    private String indicatorName;
    /**
     * 及格分数线
     */
    private Integer passScore;
    /**
     * 附件 URL 列表（JSON数组）
     */
    private String attachmentUrls;
    /**
     * 图片 URL 列表（JSON数组）
     */
    private String imageUrlsJson;
    /**
     * 音频转写文本
     */
    private String transcript;
    /**
     * 结构化证据（JSON）
     */
    private String evidenceJson;
    /**
     * 评分规则（JSON）
     */
    private String rubricJson;
    /**
     * 参考资料（JSON）
     */
    private String referenceJson;
    /** 审计使用的业务对象标识，不发送给模型。 */
    private String businessObjectId;

    /** 教师评分偏好，仅由服务端统计生成。 */
    private String calibrationHint;

}
