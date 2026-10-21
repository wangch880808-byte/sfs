import { describe, expect, it } from 'vitest';

import {
  DUE_SOON_HOURS,
  getDeadlineTone,
  getStudentTodoStatusMeta,
  isDueSoon,
} from './status-dashboard';

const HOUR = 60 * 60 * 1000;
const NOW = Date.UTC(2026, 9, 6, 12);

describe('截止时间的紧迫程度', () => {
  it(`不到 ${DUE_SOON_HOURS} 小时为即将截止（waiting），已过为 failed，其余与没有截止时间为 neutral`, () => {
    expect(getDeadlineTone(NOW + 2 * HOUR, NOW)).toBe('waiting');
    expect(getDeadlineTone(NOW + DUE_SOON_HOURS * HOUR, NOW)).toBe('waiting');
    expect(getDeadlineTone(NOW + (DUE_SOON_HOURS + 1) * HOUR, NOW)).toBe(
      'neutral',
    );
    expect(getDeadlineTone(NOW - 1, NOW)).toBe('failed');
    expect(getDeadlineTone(NOW, NOW)).toBe('failed');
    expect(getDeadlineTone(undefined, NOW)).toBe('neutral');
  });

  it('即将截止不含已过的', () => {
    expect(isDueSoon(NOW + HOUR, NOW)).toBe(true);
    expect(isDueSoon(NOW - HOUR, NOW)).toBe(false);
    expect(isDueSoon(undefined, NOW)).toBe(false);
  });
});

describe('学生待完成任务的状态', () => {
  it('未解锁优先于提交状态，其余按学生视角', () => {
    expect(getStudentTodoStatusMeta({ locked: true, status: 0 })).toEqual({
      label: '未解锁',
      tone: 'neutral',
    });
    expect(getStudentTodoStatusMeta({ status: 3 })).toEqual({
      label: '已打回',
      tone: 'waiting',
    });
    expect(getStudentTodoStatusMeta({ status: 0 })).toEqual({
      label: '未提交',
      tone: 'neutral',
    });
    expect(getStudentTodoStatusMeta({ status: null }).label).toBe('未提交');
  });
});
