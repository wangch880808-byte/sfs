import type { QueueRecord } from './use-grading-queue';

import { ref } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  countQueue,
  filterQueue,
  getAdjacentRecordId,
  getNextRecordId,
  useGradingQueue,
} from './use-grading-queue';

const api = vi.hoisted(() => ({ queue: vi.fn() }));

vi.mock('#/api/edu/homework-record', () => ({
  getHomeworkRecordGradingQueue: api.queue,
}));

function row(id: number, status: number, extra: Partial<QueueRecord> = {}) {
  return {
    homeworkId: 1,
    id,
    status,
    studentId: 100 + id,
    studentName: `学生${id}`,
    studentNo: `2026${id}`,
    ...extra,
  } as QueueRecord;
}

const records = [row(1, 1), row(2, 2), row(3, 4), row(4, 3), row(5, 1)];

describe('批改队列的筛选与顺序', () => {
  it('待批改含已重新提交；搜索按姓名或学号', () => {
    expect(filterQueue(records, 1, '').map((r) => r.id)).toEqual([1, 3, 5]);
    expect(filterQueue(records, 2, '').map((r) => r.id)).toEqual([2]);
    expect(filterQueue(records, 'all', '20265').map((r) => r.id)).toEqual([5]);
    expect(filterQueue(records, 'all', '学生3').map((r) => r.id)).toEqual([3]);
  });

  it('“下一份”取当前筛选顺序里排在后面的一份，最后一份时为空', () => {
    const pending = filterQueue(records, 1, '');
    expect(getNextRecordId(pending, 1)).toBe(3);
    expect(getNextRecordId(pending, 3)).toBe(5);
    expect(getNextRecordId(pending, 5)).toBeUndefined();
    // 全部筛选下按列表顺序，不跳过已批改
    expect(getNextRecordId(records, 1)).toBe(2);
    expect(getNextRecordId(records, 99)).toBeUndefined();
  });

  it('键盘上下切换；不在列表里时从第一份开始', () => {
    expect(getAdjacentRecordId(records, 2, 1)).toBe(3);
    expect(getAdjacentRecordId(records, 2, -1)).toBe(1);
    expect(getAdjacentRecordId(records, 1, -1)).toBeUndefined();
    expect(getAdjacentRecordId(records, 99, 1)).toBe(1);
    expect(getAdjacentRecordId([], 1, 1)).toBeUndefined();
  });

  it('统计已批改与待批改', () => {
    expect(countQueue(records)).toEqual({ corrected: 1, pending: 3 });
  });
});

describe('useGradingQueue', () => {
  beforeEach(() => api.queue.mockReset());

  it('总数来自服务端，不按 200 条截断；保存后只改一行', async () => {
    const many = Array.from({ length: 450 }, (_, i) => row(i + 1, 1));
    api.queue.mockResolvedValue({ list: many, total: 450 });
    const queue = useGradingQueue(ref(7));

    expect(await queue.load()).toBe(true);
    expect(api.queue).toHaveBeenCalledWith(7);
    expect(queue.total.value).toBe(450);
    expect(queue.records.value).toHaveLength(450);
    expect(queue.counts.value.pending).toBe(450);

    queue.patch(3, { score: 88, status: 2 });
    expect(queue.findRecord(3)).toMatchObject({ score: 88, status: 2 });
    expect(queue.findRecord(4)?.status).toBe(1);
    queue.statusFilter.value = 1;
    expect(queue.filteredRecords.value).toHaveLength(449);
  });

  it('后发起的读取覆盖先发起的；读取失败标记错误', async () => {
    let resolveFirst: (value: unknown) => void = () => {};
    api.queue
      .mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce({ list: [row(9, 1)], total: 1 });
    const queue = useGradingQueue(ref(7));
    const first = queue.load();
    expect(await queue.load()).toBe(true);
    resolveFirst({ list: [row(1, 1), row(2, 1)], total: 2 });
    expect(await first).toBe(false);
    expect(queue.records.value.map((r) => r.id)).toEqual([9]);

    api.queue.mockRejectedValueOnce(new Error('network'));
    expect(await queue.load()).toBe(false);
    expect(queue.loadError.value).toBe(true);
  });
});
