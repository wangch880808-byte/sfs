import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getUndoText, useGradingUndo } from './use-grading-undo';

const api = vi.hoisted(() => ({ undo: vi.fn() }));

vi.mock('#/api/edu/homework-record', () => ({
  undoHomeworkGrading: api.undo,
}));

vi.mock('vue', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue')>()),
  onBeforeUnmount: vi.fn(),
}));

const EMPTY = { kind: 'correct', remaining: 0, studentName: '' } as const;

describe('批改撤销', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    api.undo.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it('按后端给的秒数倒计时，到点收起', () => {
    let now = 0;
    const undo = useGradingUndo(() => now);
    undo.present(
      { id: 1, status: 2, undoSeconds: 3, undoToken: 55 },
      'correct',
      '张三',
    );
    expect(undo.offer.value).toMatchObject({ remaining: 3, token: 55 });
    expect(getUndoText(undo.offer.value ?? EMPTY)).toEqual({
      action: '撤销（3 秒）',
      text: '已保存「张三」的批改',
    });
    now = 1000;
    vi.advanceTimersByTime(1000);
    expect(undo.offer.value?.remaining).toBe(2);
    now = 3000;
    vi.advanceTimersByTime(2000);
    expect(undo.offer.value).toBeUndefined();
  });

  it('没有撤销凭据或秒数为 0 时不显示', () => {
    const undo = useGradingUndo();
    undo.present(
      { id: 1, status: 2, undoSeconds: 0, undoToken: 55 },
      'correct',
      '张三',
    );
    expect(undo.offer.value).toBeUndefined();
    undo.present({ id: 1, status: 3 }, 'redo', '张三');
    expect(undo.offer.value).toBeUndefined();
  });

  it('撤销成功返回恢复后的记录；失败写原因、不弹全局提示', async () => {
    const undo = useGradingUndo();
    undo.present(
      { id: 1, status: 3, undoSeconds: 10, undoToken: 56 },
      'redo',
      '李四',
    );
    expect(getUndoText(undo.offer.value ?? EMPTY).text).toBe(
      '已打回「李四」的提交',
    );
    api.undo.mockResolvedValueOnce({ id: 1, status: 1 });
    await expect(undo.undo()).resolves.toEqual({ id: 1, status: 1 });
    expect(api.undo).toHaveBeenCalledWith(56, { silentError: true });
    expect(undo.offer.value).toBeUndefined();

    undo.present(
      { id: 1, status: 2, undoSeconds: 10, undoToken: 57 },
      'correct',
      '李四',
    );
    api.undo.mockRejectedValueOnce({
      data: { code: 1_050_005_103, msg: '撤销没有完成：已超过可撤销时间' },
    });
    await expect(undo.undo()).resolves.toBeUndefined();
    expect(undo.errorText.value).toBe('撤销没有完成：已超过可撤销时间');
    expect(undo.offer.value).toBeUndefined();
  });
});
