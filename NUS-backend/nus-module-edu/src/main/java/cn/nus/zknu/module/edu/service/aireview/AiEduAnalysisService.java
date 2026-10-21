package cn.nus.zknu.module.edu.service.aireview;

import reactor.core.publisher.Flux;

/**
 * AI分析 Service 接口
 */
public interface AiEduAnalysisService {

    /**
     * 班级得分分析（流式）
     */
    Flux<String> analyzeClassScoreStream(Long classId, Long courseId, Long homeworkId);

}
