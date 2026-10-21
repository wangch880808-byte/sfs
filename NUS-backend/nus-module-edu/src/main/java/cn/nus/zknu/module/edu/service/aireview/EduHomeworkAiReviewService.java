package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;

public interface EduHomeworkAiReviewService {
    EduHomeworkAiReviewDO generatePreReviewForTeacher(Long recordId, Long teacherId);

    /**
     * 提交、重交时调用：在调用方的业务事务内写入一条「排队中」的 AI 初评，事务提交后才派发执行；
     * 事务回滚则一并撤销。线程池已满时记录保持排队中，由派发器定时补派。
     * 任务没有开启 AI 初评时不写入；系统关闭 AI 初评时写入「已跳过」并注明原因。
     */
    void enqueuePreReview(Long homeworkRecordId);

    /**
     * 执行一条排队中的 AI 初评：先以「排队中 → 处理中」的条件更新认领，没抢到返回 null；
     * 放弃执行的每条路径都会写成「失败」或「已跳过」并注明原因。
     */
    EduHomeworkAiReviewDO processQueuedReview(Long reviewId);

    /** 兼容旧调用，等同于 {@link #enqueuePreReview(Long)}。 */
    void triggerPreReviewAsync(Long homeworkRecordId);

    void triggerPreReviewAsync(Long homeworkRecordId, boolean force);

    EduHomeworkAiReviewDO generatePreReview(Long homeworkRecordId, boolean force);

    EduHomeworkAiReviewDO getLatestByRecordId(Long homeworkRecordId);

    /** 已由批改流程完成记录归属校验后，读取关联草案。 */
    EduHomeworkAiReviewDO getLatestForCorrection(Long homeworkRecordId);

    void applyLatestReview(Long homeworkRecordId);

    void rejectLatestReview(Long homeworkRecordId);

    void markTeacherReviewed(Long homeworkRecordId, Integer score, String comment);

    String buildStudentCommentSuggestion(Long homeworkRecordId);

    Double getConfidenceThreshold(Long homeworkRecordId);
}
