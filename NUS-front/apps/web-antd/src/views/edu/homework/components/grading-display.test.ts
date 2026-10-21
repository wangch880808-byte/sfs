import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  formatShortDate,
  formatShortTime,
  getCorrectBlockedReason,
  getGradingSummary,
  getRecordListMeta,
  getScoreLabel,
} from './grading-display';

const at = (value: string) => dayjs(value).valueOf();

describe('批改工作台的显示文字', () => {
  it('短时间只留月日与时分，解析不了时为空', () => {
    expect(formatShortTime('2026-10-06 19:46:10')).toBe('10-06 19:46');
    expect(formatShortTime(at('2026-10-13 23:59:00'))).toBe('10-13 23:59');
    expect(formatShortTime(undefined)).toBe('');
    expect(formatShortTime('不是时间')).toBe('');
    expect(formatShortDate('2026-10-13 23:59:00')).toBe('10-13');
    expect(formatShortDate(null)).toBe('');
  });

  it('列表第二行写学号与提交时间，已打回的写重做截止日期', () => {
    expect(
      getRecordListMeta({
        status: 1,
        studentNo: 'demostudent3',
        submitTime: '2026-10-06 19:46:10',
      }),
    ).toBe('demostudent3 · 10-06 19:46');
    expect(
      getRecordListMeta({
        redoDeadline: '2026-10-13 23:59:00',
        status: 3,
        studentNo: 'demostudent2',
        submitTime: '2026-10-06 19:46:10',
      }),
    ).toBe('demostudent2 · 重做截止 10-13');
    expect(getRecordListMeta({ status: 0, studentNo: 'demostudent4' })).toBe(
      'demostudent4',
    );
    expect(
      getRecordListMeta({ status: 1, submitTime: '2026-10-06 08:00' }),
    ).toBe('10-06 08:00');
  });

  it('已打回与已重新提交的分数是原成绩', () => {
    expect(getScoreLabel(2)).toBe('得分');
    expect(getScoreLabel(3)).toBe('原成绩');
    expect(getScoreLabel(4)).toBe('原成绩');
  });

  it('能批改的记录没有原因，未提交与已打回写明怎么解锁', () => {
    for (const status of [1, 2, 4]) {
      expect(getCorrectBlockedReason(status)).toBe('');
    }
    expect(getCorrectBlockedReason(0)).toContain('提交后才能批改');
    expect(getCorrectBlockedReason(3)).toContain('重新提交后才能批改');
    expect(getCorrectBlockedReason(undefined)).not.toBe('');
  });

  it('页头说明一行写完记录数与截止时间', () => {
    expect(
      getGradingSummary({
        corrected: 1,
        deadline: '2026-10-20 23:59:00',
        pending: 1,
        total: 3,
      }),
    ).toBe('共 3 条记录 · 已批改 1 · 待批改 1 · 截止 2026-10-20 23:59');
    expect(getGradingSummary({ corrected: 0, pending: 0, total: 0 })).toBe(
      '共 0 条记录 · 已批改 0 · 待批改 0',
    );
  });
});
