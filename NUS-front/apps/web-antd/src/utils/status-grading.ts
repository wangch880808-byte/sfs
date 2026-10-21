import type { StatusMeta } from './status';

import { HOMEWORK_RECORD_STATUS } from './status';

/**
 * 批改工作台的状态呈现（V1.0.1.6 界面规范）：AI 初评草案上的风险标记。
 * 低置信度、必须人工复核两类要逐项核对，用 failed；其余提醒注意，用 waiting。
 * 不认识的标记不显示原值，统一写成“其他风险标记”。
 */
const AI_RISK_FLAG_META: Record<string, StatusMeta> = {
  audio_transcript_empty: { label: '音频转写为空', tone: 'waiting' },
  invalid_model_risk_flags: { label: '模型风险标记异常', tone: 'waiting' },
  low_confidence: { label: '低置信度', tone: 'failed' },
  manual_review_required: { label: '必须人工复核', tone: 'failed' },
  no_effective_evidence: { label: '有效证据不足', tone: 'waiting' },
  video_manual_review_only: { label: '视频只能人工批改', tone: 'waiting' },
};

const UNKNOWN_RISK_FLAG: StatusMeta = {
  label: '其他风险标记',
  tone: 'waiting',
};

export function getAiRiskFlagMeta(flag: null | string | undefined): StatusMeta {
  if (!flag) {
    return UNKNOWN_RISK_FLAG;
  }
  return AI_RISK_FLAG_META[flag] ?? UNKNOWN_RISK_FLAG;
}

type RecordStatus = null | number | undefined;

/** AI 初评状态（edu_homework_ai_review.status） */
export const AI_PRE_REVIEW_STATUS = {
  QUEUED: 0,
  PROCESSING: 1,
  READY: 2,
  FAILED: 3,
  ACCEPTED: 4,
  REJECTED: 5,
  SKIPPED: 6,
} as const;

/** 批量 AI 初评任务状态（ai_edu_review_task.status） */
export const AI_BATCH_TASK_STATUS = {
  RUNNING: 0,
  DONE: 1,
  PARTIAL_FAILED: 2,
  FAILED: 3,
} as const;

/** 等教师批改：首次提交与打回后重新提交 */
export function isPendingReviewStatus(status: RecordStatus): boolean {
  return (
    status === HOMEWORK_RECORD_STATUS.SUBMITTED ||
    status === HOMEWORK_RECORD_STATUS.RESUBMITTED
  );
}

/** 教师可以批改（含对已批改的记录复评） */
export function isCorrectableStatus(status: RecordStatus): boolean {
  return (
    isPendingReviewStatus(status) || status === HOMEWORK_RECORD_STATUS.CORRECTED
  );
}

/** 已打回：再次打回只调整重做期限 */
export function isRedoStatus(status: RecordStatus): boolean {
  return status === HOMEWORK_RECORD_STATUS.REDO;
}

/** 列表与详情显示分数：已批改的本次成绩，已打回、已重新提交的原成绩 */
export function hasVisibleScoreStatus(status: RecordStatus): boolean {
  return (
    status === HOMEWORK_RECORD_STATUS.CORRECTED ||
    status === HOMEWORK_RECORD_STATUS.REDO ||
    status === HOMEWORK_RECORD_STATUS.RESUBMITTED
  );
}

/** 分数是上一次的成绩（已打回、已重新提交） */
export function isPreviousScoreStatus(status: RecordStatus): boolean {
  return (
    status === HOMEWORK_RECORD_STATUS.REDO ||
    status === HOMEWORK_RECORD_STATUS.RESUBMITTED
  );
}

/** AI 初评还在排队或处理中：需要轮询 */
export function isAiPreReviewPending(status: RecordStatus): boolean {
  return (
    status === AI_PRE_REVIEW_STATUS.QUEUED ||
    status === AI_PRE_REVIEW_STATUS.PROCESSING
  );
}

/** 批量 AI 初评任务以失败或部分失败结束 */
export function isAiBatchTaskFailed(status: RecordStatus): boolean {
  return (
    status === AI_BATCH_TASK_STATUS.PARTIAL_FAILED ||
    status === AI_BATCH_TASK_STATUS.FAILED
  );
}
