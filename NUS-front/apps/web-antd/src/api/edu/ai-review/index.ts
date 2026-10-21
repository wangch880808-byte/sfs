import type { RequestClientConfig } from '@vben/request';

import { requestClient } from '#/api/request';

import { parseAiTaskStartResponse } from './contracts';
import { streamAiCommonResult } from './sse';

export namespace AiReviewApi {
  export type CalibrationScope = 'COURSE' | 'NONE' | 'TASK';
  export type CalibrationDirection =
    | 'INSUFFICIENT'
    | 'LOOSER'
    | 'NONE'
    | 'STRICTER';

  export interface CalibrationRespVO {
    scope: CalibrationScope;
    sampleCount: number;
    strictCount: number;
    normalCount: number;
    looseCount: number;
    averageDeviation: number;
    fullScore: number;
    direction: CalibrationDirection;
    hint: string;
  }

  /** 单份AI批改请求 */
  export interface CorrectOneReqVO {
    homeworkRecordId: number;
  }

  /** 单份AI批改响应 */
  export interface CorrectOneRespVO {
    score: number;
    comment: string;
    suggestions: string;
  }

  /** 异步批改请求 */
  export interface CorrectAsyncReqVO {
    homeworkRecordId?: number;
    homeworkId?: number;
    classId?: number;
  }

  /** 异步任务响应 */
  export interface AsyncTaskRespVO {
    taskId: number;
  }

  /** 任务状态响应 */
  export interface TaskStatusRespVO {
    taskId: number;
    status: number; // 0=处理中,1=完成,2=部分失败,3=失败
    progress: number; // 0-100
    totalCount: number;
    successCount: number;
    failCount: number;
    errorMessage?: string;
  }

  /** AI评分反馈请求 */
  export interface FeedbackReqVO {
    homeworkRecordId: number;
    feedbackType: 'loose' | 'normal' | 'strict';
  }

  /** 班级分析请求 */
  export interface ClassAnalysisReqVO {
    classId: number;
    courseId: number;
    homeworkId: number;
  }
}

/** 当前教师对该任务的 AI 评分校准状态 */
export function getAiCalibration(
  homeworkId: number,
  config?: RequestClientConfig,
) {
  return requestClient.get<AiReviewApi.CalibrationRespVO>(
    `/edu/ai-review/calibration?homeworkId=${homeworkId}`,
    config,
  );
}

/** 单份AI批改（异步） */
export function aiCorrectOneAsync(data: AiReviewApi.CorrectAsyncReqVO) {
  return requestClient
    .post<AiReviewApi.AsyncTaskRespVO>('/edu/ai-review/correct-one-async', data)
    .then(parseAiTaskStartResponse);
}

/** 全部AI批改（异步） */
export function aiCorrectAllAsync(data: AiReviewApi.CorrectAsyncReqVO) {
  return requestClient
    .post<AiReviewApi.AsyncTaskRespVO>('/edu/ai-review/correct-all-async', data)
    .then(parseAiTaskStartResponse);
}

/** 查询任务状态 */
/** config 可传 silentError：轮询出错由调用方提示，不再弹全局提示 */
export function getAiTaskStatus(taskId: number, config?: RequestClientConfig) {
  return requestClient.get<AiReviewApi.TaskStatusRespVO>(
    `/edu/ai-review/task-status?taskId=${taskId}`,
    config,
  );
}

/** AI评分反馈 */
export function submitAiFeedback(data: AiReviewApi.FeedbackReqVO) {
  return requestClient.post('/edu/ai-review/feedback', data);
}

/** 班级得分分析（流式） */
export function streamClassAnalysis({
  ctrl,
  data,
  onMessage,
}: {
  ctrl: AbortController;
  data: AiReviewApi.ClassAnalysisReqVO;
  onMessage: (content: string) => void;
}) {
  return streamAiCommonResult({
    ctrl,
    data,
    onData: onMessage,
    path: '/edu/ai-review/analysis/class-stream',
  });
}
