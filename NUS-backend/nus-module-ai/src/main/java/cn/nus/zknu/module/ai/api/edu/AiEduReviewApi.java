package cn.nus.zknu.module.ai.api.edu;

import cn.nus.zknu.module.ai.api.edu.dto.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * AI教育评阅 API 接口
 * 供 edu 模块跨模块调用
 */
public interface AiEduReviewApi {

    /**
     * 同步批改作业
     */
    AiEduReviewRespDTO correctHomework(AiEduReviewReqDTO req);

    /**
     * 生成评语
     */
    String generateComment(AiEduGenerateReqDTO req);

    /**
     * 改写题库题目为新的实训任务题目
     */
    AiEduQuestionRewriteRespDTO rewriteQuestion(AiEduQuestionRewriteReqDTO req);

    /**
     * 流式生成评语/分析报告
     */
    Flux<String> generateCommentStream(AiEduGenerateReqDTO req);

    /**
     * 单次教育 AI 同步调用的总时间上限（配置项 nus.ai.edu.hard-timeout），供调用方估算处理时限。
     */
    Duration getReviewHardTimeout();

}
