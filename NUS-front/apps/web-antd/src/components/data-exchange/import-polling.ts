import type { EduDataExchangeApi } from '#/api/edu/data-exchange';

/** 确认导入改为后台执行（V1.0.1.8）：批次处于这些状态时导入已经结束 */
const FINISHED_STATUSES = new Set<EduDataExchangeApi.BatchStatus>([
  'CANCELLED',
  'FAILED',
  'SUCCESS',
]);

export function isImportFinished(status?: EduDataExchangeApi.BatchStatus) {
  return !!status && FINISHED_STATUSES.has(status);
}

/** 请求被前端超时截断（后端可能仍在执行），此时改为查询批次状态，而不是当作失败 */
export function isRequestTimeout(error: unknown) {
  const value = error as undefined | { code?: string; message?: string };
  return (
    value?.code === 'ECONNABORTED' ||
    String(value?.message ?? '').includes('timeout')
  );
}

export interface WaitForImportOptions {
  /** 两次查询的间隔，默认 2 秒 */
  intervalMs?: number;
  /** 每拿到一次状态就回调，用于刷新进度 */
  onUpdate?: (batch: EduDataExchangeApi.ImportBatch) => void;
  /** 返回 true 时停止等待（例如窗口已关闭），结果为 undefined */
  shouldStop?: () => boolean;
  /** 单次查询失败（网络抖动）时继续等待的次数，默认 5 */
  maxConsecutiveErrors?: number;
}

/**
 * 轮询批次直到导入结束（成功、失败或取消）。查询偶尔失败时重试，连续失败超过上限才抛出。
 */
export async function waitForImportBatch(
  fetchBatch: () => Promise<EduDataExchangeApi.ImportBatch>,
  options: WaitForImportOptions = {},
): Promise<EduDataExchangeApi.ImportBatch | undefined> {
  const intervalMs = options.intervalMs ?? 2000;
  const maxErrors = options.maxConsecutiveErrors ?? 5;
  let errors = 0;
  while (!options.shouldStop?.()) {
    try {
      const batch = await fetchBatch();
      errors = 0;
      options.onUpdate?.(batch);
      if (isImportFinished(batch.status)) {
        return batch;
      }
    } catch (error) {
      errors += 1;
      if (errors > maxErrors) {
        throw error;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  return undefined;
}
