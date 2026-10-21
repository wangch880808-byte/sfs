import type { RequestClientConfig } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace EduHomeworkAiReviewApi {
  export interface HomeworkAiReview {
    id: number;
    homeworkRecordId: number;
    status: number;
    submissionMode?: string;
    aiScore?: number;
    aiComment?: string;
    studentCommentSuggestion?: string;
    aiConfidence?: number;
    confidenceThreshold?: number;
    transcript?: string;
    evidenceJson?: string;
    subScoresJson?: string;
    riskFlagsJson?: string;
    teacherDecision?: string;
    teacherScore?: number;
    teacherComment?: string;
    errorMessage?: string;
  }
}

/** config 可传 silentError：轮询与页面里已有错误态的读取不弹全局提示 */
export function getHomeworkAiReviewByRecord(
  homeworkRecordId: number,
  config?: RequestClientConfig,
) {
  return requestClient.get<EduHomeworkAiReviewApi.HomeworkAiReview>(
    `/edu/homework-ai-review/get-by-record?homeworkRecordId=${homeworkRecordId}`,
    config,
  );
}

export function rejectHomeworkAiReview(
  homeworkRecordId: number,
  config?: RequestClientConfig,
) {
  return requestClient.put(
    `/edu/homework-ai-review/reject?homeworkRecordId=${homeworkRecordId}`,
    undefined,
    config,
  );
}

/** 单份触发：入队后立即返回；状态不对、AI 未开启时直接报错，config 可传 silentError 由页面就地提示 */
export function triggerHomeworkAiReview(
  homeworkRecordId: number,
  config?: RequestClientConfig,
) {
  return requestClient.post(
    `/edu/homework-ai-review/trigger?homeworkRecordId=${homeworkRecordId}`,
    undefined,
    config,
  );
}
