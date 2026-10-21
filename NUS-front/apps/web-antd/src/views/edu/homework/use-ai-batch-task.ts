import type { AiReviewApi } from '#/api/edu/ai-review';
import type { AiPollingHandle } from '#/utils/ai-polling';

import { ref } from 'vue';

import { getAiTaskStatus } from '#/api/edu/ai-review';
import { startAiPolling } from '#/utils/ai-polling';
import {
  AI_BATCH_TASK_STATUS,
  isAiBatchTaskFailed,
} from '#/utils/status-grading';

/**
 * 批量生成 AI 初评的进度弹窗：串行轮询任务状态，结束后通知页面刷新列表。
 * 轮询不弹全局提示，读不到进度时写在弹窗里。
 */
const SILENT = { silentError: true } as const;

export const AI_BATCH_TEXT = {
  readFailed: '没有读到批量 AI 初评的进度，请稍后刷新。',
  starting: '正在启动批量生成…',
} as const;

export function useAiBatchTask(onFinished: (succeeded: boolean) => void) {
  const open = ref(false);
  const progress = ref(0);
  const total = ref(0);
  const completed = ref(0);
  const failed = ref(0);
  const progressText = ref('');
  const errorText = ref('');
  const timedOut = ref(false);
  const polling = ref<AiPollingHandle>();
  let activeTaskId: number | undefined;

  function apply(status: AiReviewApi.TaskStatusRespVO) {
    progress.value = status.progress;
    total.value = status.totalCount;
    completed.value = status.successCount;
    failed.value = status.failCount;
    progressText.value = `已完成 ${status.successCount + status.failCount} / ${status.totalCount}`;
    errorText.value = isAiBatchTaskFailed(status.status)
      ? status.errorMessage || ''
      : '';
  }

  function stop() {
    polling.value?.stop();
    polling.value = undefined;
    activeTaskId = undefined;
  }

  function start(taskId: number) {
    stop();
    activeTaskId = taskId;
    open.value = true;
    progress.value = 0;
    total.value = 0;
    completed.value = 0;
    failed.value = 0;
    errorText.value = '';
    timedOut.value = false;
    progressText.value = AI_BATCH_TEXT.starting;
    polling.value = startAiPolling({
      request: () => getAiTaskStatus(taskId, SILENT),
      isPending: (status) => status.status === AI_BATCH_TASK_STATUS.RUNNING,
      onResult: (status) => {
        apply(status);
        if (status.status !== AI_BATCH_TASK_STATUS.RUNNING) {
          polling.value = undefined;
          onFinished(status.status === AI_BATCH_TASK_STATUS.DONE);
        }
      },
      onError: () => {
        polling.value = undefined;
        errorText.value = AI_BATCH_TEXT.readFailed;
        timedOut.value = true;
      },
      onTimeout: () => {
        polling.value = undefined;
        timedOut.value = true;
      },
    });
  }

  /** 超时或读失败后手动刷新一次 */
  async function refresh() {
    const taskId = activeTaskId;
    if (!taskId) return;
    try {
      const status = await getAiTaskStatus(taskId, SILENT);
      if (taskId !== activeTaskId) return;
      apply(status);
      if (status.status !== AI_BATCH_TASK_STATUS.RUNNING) {
        timedOut.value = false;
        onFinished(status.status === AI_BATCH_TASK_STATUS.DONE);
      }
    } catch {
      if (taskId === activeTaskId) errorText.value = AI_BATCH_TEXT.readFailed;
    }
  }

  function close() {
    stop();
    open.value = false;
  }

  return {
    close,
    completed,
    errorText,
    failed,
    open,
    polling,
    progress,
    progressText,
    refresh,
    start,
    stop,
    timedOut,
    total,
  };
}
