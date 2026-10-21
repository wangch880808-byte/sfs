import type { EduHomeworkAiReviewApi } from '#/api/edu/homework-ai-review';
import type { AiPollingHandle } from '#/utils/ai-polling';

import { computed, ref } from 'vue';

import {
  getHomeworkAiReviewByRecord,
  rejectHomeworkAiReview,
  triggerHomeworkAiReview,
} from '#/api/edu/homework-ai-review';
import { AI_REVIEW_TEXT } from '#/utils/ai-calibration';
import { startAiPolling } from '#/utils/ai-polling';
import { getAiPreReviewStatusMeta } from '#/utils/status';
import {
  AI_PRE_REVIEW_STATUS,
  isAiPreReviewPending,
} from '#/utils/status-grading';

import { getRequestErrorMessage } from './grading-errors';

/**
 * 当前提交的 AI 初评：读取、排队与处理中轮询、手动触发、忽略。
 * 轮询与触发都不弹全局提示，出错写在 AI 初评区块里。
 */
export type AiPreReview = EduHomeworkAiReviewApi.HomeworkAiReview;

interface AiSubScore {
  key?: string;
  reason?: string;
  score?: number;
}

const SILENT = { silentError: true } as const;

function parseJsonArray<T>(value?: string): T[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** 草案区块的说明：没有初评、排队中、处理中 */
export function getAiDraftNote(
  review: AiPreReview | undefined,
  loading: boolean,
): string {
  if (!review) return loading ? '正在读取 AI 初评…' : '还没有 AI 初评。';
  if (review.status === AI_PRE_REVIEW_STATUS.QUEUED) return '排队中…';
  return 'AI 正在初评…';
}

/** 草案里第二项的标题：失败写失败原因，跳过写跳过原因，其余是初评评语 */
export function getAiDraftCommentLabel(status?: number): string {
  if (status === AI_PRE_REVIEW_STATUS.FAILED) return '失败原因';
  if (status === AI_PRE_REVIEW_STATUS.SKIPPED) return '跳过原因';
  return '初评评语';
}

/** 草案里第二项的内容：失败优先写保存的失败原因 */
export function getAiDraftCommentText(review?: AiPreReview): string {
  if (!review) return '';
  if (review.status === AI_PRE_REVIEW_STATUS.FAILED) {
    return review.errorMessage || review.aiComment || '没有记录失败原因';
  }
  return review.aiComment || review.errorMessage || 'AI 初评没有给出内容';
}

export function useAiPreReview(currentRecordId: () => number | undefined) {
  const review = ref<AiPreReview>();
  const loading = ref(false);
  const triggering = ref(false);
  const timedOut = ref(false);
  /** 就地显示的错误：读取失败、触发失败、忽略失败 */
  const errorText = ref('');
  let polling: AiPollingHandle | undefined;
  let requestId = 0;

  const riskFlags = computed(() =>
    parseJsonArray<string>(review.value?.riskFlagsJson),
  );
  const subScores = computed(() =>
    parseJsonArray<AiSubScore>(review.value?.subScoresJson),
  );
  const confidenceThreshold = computed(
    () => review.value?.confidenceThreshold ?? 0.7,
  );
  const isLowConfidence = computed(() => {
    const confidence = review.value?.aiConfidence;
    return (
      riskFlags.value.includes('low_confidence') ||
      riskFlags.value.includes('manual_review_required') ||
      (confidence !== undefined && confidence < confidenceThreshold.value)
    );
  });
  const statusMeta = computed(() =>
    getAiPreReviewStatusMeta(review.value?.status),
  );
  const isReady = computed(
    () => review.value?.status === AI_PRE_REVIEW_STATUS.READY,
  );
  const isPending = computed(() => isAiPreReviewPending(review.value?.status));
  /** 有初评记录且不在排队、处理中时显示草案内容 */
  const draftVisible = computed(
    () => Boolean(review.value) && !isPending.value,
  );
  const draftNote = computed(() => getAiDraftNote(review.value, loading.value));
  const triggerLabel = computed(() =>
    review.value?.status === AI_PRE_REVIEW_STATUS.FAILED
      ? '重新生成'
      : '手动触发初评',
  );

  function stopPolling() {
    polling?.stop();
    polling = undefined;
  }

  function reset() {
    stopPolling();
    ++requestId;
    review.value = undefined;
    loading.value = false;
    timedOut.value = false;
    errorText.value = '';
  }

  async function load(recordId: number | undefined, poll = false) {
    if (!recordId) {
      review.value = undefined;
      return;
    }
    const current = ++requestId;
    loading.value = true;
    errorText.value = '';
    try {
      const result = await getHomeworkAiReviewByRecord(recordId, SILENT);
      if (current !== requestId || currentRecordId() !== recordId) return;
      review.value = result ?? undefined;
      if (poll && result && isAiPreReviewPending(result.status)) {
        startPolling(recordId);
      }
    } catch {
      if (current === requestId) {
        review.value = undefined;
        errorText.value = AI_REVIEW_TEXT.preReviewStatusFailed;
      }
    } finally {
      if (current === requestId) loading.value = false;
    }
  }

  function startPolling(recordId: number) {
    stopPolling();
    timedOut.value = false;
    polling = startAiPolling({
      request: () => getHomeworkAiReviewByRecord(recordId, SILENT),
      isPending: (result) => !result || isAiPreReviewPending(result.status),
      onResult: (result) => {
        if (currentRecordId() !== recordId) return;
        ++requestId;
        review.value = result ?? undefined;
        loading.value = false;
        if (result && !isAiPreReviewPending(result.status)) polling = undefined;
      },
      onError: () => {
        polling = undefined;
        if (currentRecordId() === recordId) {
          errorText.value = AI_REVIEW_TEXT.preReviewStatusFailed;
        }
      },
      onTimeout: () => {
        polling = undefined;
        if (currentRecordId() === recordId) timedOut.value = true;
      },
    });
  }

  async function refresh() {
    const recordId = currentRecordId();
    if (!recordId) return;
    await load(recordId);
    if (review.value && !isPending.value) timedOut.value = false;
  }

  /** 手动触发（失败后重新生成）：入队即返回，之后轮询；状态不对、AI 未开启等错误写在区块里 */
  async function trigger(): Promise<boolean> {
    const recordId = currentRecordId();
    if (!recordId) return false;
    triggering.value = true;
    errorText.value = '';
    try {
      await triggerHomeworkAiReview(recordId, SILENT);
      if (currentRecordId() !== recordId) return true;
      await load(recordId);
      if (
        currentRecordId() === recordId &&
        (!review.value || isPending.value)
      ) {
        startPolling(recordId);
      }
      return true;
    } catch (error) {
      if (currentRecordId() === recordId) {
        errorText.value = getRequestErrorMessage(
          error,
          'AI 初评没有触发，请稍后重试。',
        );
      }
      return false;
    } finally {
      triggering.value = false;
    }
  }

  async function reject(): Promise<boolean> {
    const recordId = currentRecordId();
    if (!recordId) return false;
    errorText.value = '';
    try {
      await rejectHomeworkAiReview(recordId, SILENT);
      if (currentRecordId() === recordId) await load(recordId);
      return true;
    } catch (error) {
      if (currentRecordId() === recordId) {
        errorText.value = getRequestErrorMessage(
          error,
          '忽略 AI 初评没有成功，请稍后重试。',
        );
      }
      return false;
    }
  }

  /** 填入批改表单用的分数与评语；没有可用草案时为空 */
  function draftForForm(): undefined | { comment?: string; score?: number } {
    const current = review.value;
    if (!current) return undefined;
    return {
      comment:
        current.studentCommentSuggestion || current.aiComment || undefined,
      score:
        current.aiScore === undefined || current.aiScore === null
          ? undefined
          : current.aiScore,
    };
  }

  return {
    confidenceThreshold,
    draftForForm,
    draftNote,
    draftVisible,
    errorText,
    isLowConfidence,
    isPending,
    isReady,
    load,
    loading,
    refresh,
    reject,
    reset,
    review,
    riskFlags,
    startPolling,
    statusMeta,
    stopPolling,
    subScores,
    timedOut,
    trigger,
    triggering,
    triggerLabel,
  };
}
