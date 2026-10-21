package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCorrectRespVO;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewTaskStatusRespVO;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;

/**
 * AI评阅 Service 接口
 */
public interface AiEduReviewService {

    /**
     * 单份同步批改
     */
    AiReviewCorrectRespVO correctOne(Long homeworkRecordId);

    /**
     * 全部异步批改，返回任务ID
     */
    Long correctAllAsync(Long homeworkId, Long classId);

    /**
     * 查询任务状态
     */
    AiReviewTaskStatusRespVO getTaskStatus(Long taskId);

    /**
     * 提交评分反馈
     */
    void submitFeedback(Long homeworkRecordId, String feedbackType);

    AiReviewCalibrationRespVO getCalibration(Long homeworkId);

}
