package cn.nus.zknu.module.edu.service.aireview;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;

import java.util.Locale;

/**
 * AI 初评失败、跳过的原因文字。教师在批改页「失败原因」与通知里看到的都是这里的句子，
 * 按「什么没成功—原因—下一步」写。
 */
public final class HomeworkAiReviewFailures {

    public static final String RECORD_MISSING_CAUSE = "这份提交已不存在";
    public static final String HOMEWORK_MISSING_CAUSE = "所属实训任务已不存在";
    public static final String WRONG_STATUS_CAUSE = "这份提交已被批改或打回，不在待批改状态";
    public static final String MODEL_TIMEOUT_CAUSE = "AI 模型响应超时";
    public static final String PROCESSING_TIMEOUT_CAUSE = "处理时间过长或服务重启，没有完成";

    public static final String VIDEO_SKIPPED = "视频只做存档，不做 AI 初评，请你直接批改。";
    public static final String SUPERSEDED_SKIPPED = "学生已重新提交，这次 AI 初评作废，以最新提交的初评为准。";
    public static final String DISABLED_SKIPPED = "系统没有开启 AI 初评，请你直接批改；需要开启请联系管理员。";

    static final String RETRY_HINT = "你可以在批改页点「手动触发初评」或「批量生成 AI 初评」重新生成。";
    static final int MAX_DETAIL_LENGTH = 120;
    static final int MAX_MESSAGE_LENGTH = 1000;

    private HomeworkAiReviewFailures() {
    }

    /** 排队过久：线程池长期占满，排队记录一直没有轮到。 */
    public static String queueOverflowCause(int minutes) {
        return "排队超过 " + minutes + " 分钟仍未开始，AI 初评队列积压";
    }

    /** 模型调用抛出的异常转成一句原因；超时单独说明。 */
    public static String modelFailureCause(Throwable ex) {
        if (isTimeout(ex)) {
            return MODEL_TIMEOUT_CAUSE;
        }
        String detail = ex == null ? null : StrUtil.trim(ex.getMessage());
        if (StrUtil.isBlank(detail)) {
            return "AI 模型调用出错（" + (ex == null ? "原因未知" : ex.getClass().getSimpleName()) + "）";
        }
        return "AI 模型调用出错（" + truncate(detail, MAX_DETAIL_LENGTH) + "）";
    }

    /** 失败说明：可重试的附上重新生成的位置。 */
    public static String failedMessage(String cause, boolean retryable) {
        String message = "AI 初评没有完成：" + StrUtil.blankToDefault(cause, "原因未知") + "。"
                + (retryable ? RETRY_HINT : "不需要再生成。");
        return truncate(message, MAX_MESSAGE_LENGTH - 1);
    }

    /** 超长截断，末尾用「…」。 */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }

    static EduHomeworkAiReviewDO failed(Long reviewId, String cause) {
        return failed(reviewId, cause, true);
    }

    static EduHomeworkAiReviewDO failed(Long reviewId, String cause, boolean retryable) {
        EduHomeworkAiReviewDO failed = new EduHomeworkAiReviewDO();
        failed.setId(reviewId);
        failed.setStatus(HomeworkAiReviewStatusEnum.FAILED);
        failed.setErrorMessage(failedMessage(cause, retryable));
        return failed;
    }

    static EduHomeworkAiReviewDO skipped(Long reviewId, String reason) {
        EduHomeworkAiReviewDO skipped = new EduHomeworkAiReviewDO();
        skipped.setId(reviewId);
        skipped.setStatus(HomeworkAiReviewStatusEnum.SKIPPED);
        skipped.setErrorMessage(reason);
        return skipped;
    }

    static boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof java.util.concurrent.TimeoutException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (message.contains("超时") || lower.contains("timed out") || lower.contains("timeout")) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
