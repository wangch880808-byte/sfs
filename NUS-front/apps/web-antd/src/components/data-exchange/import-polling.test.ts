import type { EduDataExchangeApi } from '#/api/edu/data-exchange';

import { describe, expect, it, vi } from 'vitest';

import {
  isImportFinished,
  isRequestTimeout,
  waitForImportBatch,
} from './import-polling';

function batch(
  status: EduDataExchangeApi.BatchStatus,
): EduDataExchangeApi.ImportBatch {
  return { batchNo: 'IMP-1', status } as EduDataExchangeApi.ImportBatch;
}

describe('导入状态轮询', () => {
  it('成功、失败、取消算结束，导入中和预检完成不算', () => {
    expect(isImportFinished('SUCCESS')).toBe(true);
    expect(isImportFinished('FAILED')).toBe(true);
    expect(isImportFinished('CANCELLED')).toBe(true);
    expect(isImportFinished('CONFIRMING')).toBe(false);
    expect(isImportFinished('PREVIEW_READY')).toBe(false);
    expect(isImportFinished()).toBe(false);
  });

  it('识别前端超时', () => {
    expect(isRequestTimeout({ code: 'ECONNABORTED' })).toBe(true);
    expect(isRequestTimeout(new Error('timeout of 30000ms exceeded'))).toBe(
      true,
    );
    expect(isRequestTimeout(new Error('Request failed'))).toBe(false);
  });

  it('一直查到导入结束，并逐次回调进度', async () => {
    const fetchBatch = vi
      .fn()
      .mockResolvedValueOnce(batch('CONFIRMING'))
      .mockResolvedValueOnce(batch('CONFIRMING'))
      .mockResolvedValueOnce(batch('SUCCESS'));
    const updates: string[] = [];
    const result = await waitForImportBatch(fetchBatch, {
      intervalMs: 1,
      onUpdate: (item) => updates.push(item.status),
    });
    expect(result?.status).toBe('SUCCESS');
    expect(updates).toEqual(['CONFIRMING', 'CONFIRMING', 'SUCCESS']);
  });

  it('偶尔查询失败时继续等待，连续失败超过上限才报错', async () => {
    const flaky = vi
      .fn()
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockResolvedValueOnce(batch('FAILED'));
    const recovered = await waitForImportBatch(flaky, { intervalMs: 1 });
    expect(recovered?.status).toBe('FAILED');

    const broken = vi.fn().mockRejectedValue(new Error('Network Error'));
    await expect(
      waitForImportBatch(broken, { intervalMs: 1, maxConsecutiveErrors: 2 }),
    ).rejects.toThrow('Network Error');
    expect(broken).toHaveBeenCalledTimes(3);
  });

  it('窗口关闭后停止等待', async () => {
    let stop = false;
    const fetchBatch = vi.fn(async () => {
      stop = true;
      return batch('CONFIRMING');
    });
    const result = await waitForImportBatch(fetchBatch, {
      intervalMs: 1,
      shouldStop: () => stop,
    });
    expect(result).toBeUndefined();
    expect(fetchBatch).toHaveBeenCalledTimes(1);
  });
});
