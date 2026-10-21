import type { EduDataExchangeApi } from '#/api/edu/data-exchange';

import { describe, expect, it } from 'vitest';

import {
  formatSkipBreakdown,
  getSkipBreakdown,
  getSkipReasonLabel,
  hasNothingToWrite,
} from './import-summary';

function batch(
  overrides: Partial<EduDataExchangeApi.ImportBatch>,
): EduDataExchangeApi.ImportBatch {
  return {
    batchNo: 'IMP-1',
    bizType: 'HOMEWORK_GRADEBOOK',
    createTime: '2026-10-07 10:00',
    hasErrorFile: false,
    insertedCount: 0,
    invalidCount: 0,
    skippedCount: 0,
    sourceFileName: '成绩册.xlsx',
    status: 'PREVIEW_READY',
    templateVersion: 'V1',
    totalCount: 0,
    updatedCount: 0,
    validCount: 0,
    ...overrides,
  };
}

describe('导入预检摘要', () => {
  it('跳过原因按固定顺序拆分，去掉 0 条的类别', () => {
    expect(
      getSkipBreakdown({ EMPTY: 2, NOT_SUBMITTED: 0, UNCHANGED: 3 }),
    ).toEqual([
      { count: 3, key: 'UNCHANGED', label: '未改动' },
      { count: 2, key: 'EMPTY', label: '未填成绩' },
    ]);
    expect(
      formatSkipBreakdown({
        AWAITING_RESUBMIT: 1,
        EMPTY: 2,
        PRE_REDO_SCORE: 4,
        UNCHANGED: 3,
      }),
    ).toBe('未改动 3 · 未填成绩 2 · 打回前成绩 4 · 等待重新提交 1');
  });

  it('没有细分原因或遇到不认识的原因时也能显示', () => {
    expect(getSkipBreakdown()).toEqual([]);
    expect(formatSkipBreakdown()).toBe('');
    expect(formatSkipBreakdown({ SOMETHING_NEW: 1, UNCHANGED: 1 })).toBe(
      '未改动 1 · 其他原因 1',
    );
  });

  it('单行跳过原因', () => {
    expect(getSkipReasonLabel('UNCHANGED')).toBe('成绩和评语都没有改动');
    expect(getSkipReasonLabel('EMPTY')).toBe('待批改，没有填写成绩');
    expect(getSkipReasonLabel(undefined)).toBe('—');
    expect(getSkipReasonLabel('SOMETHING_NEW')).toBe('其他原因');
  });

  it('没有要写入的行时提示确认导入不会改动数据', () => {
    expect(hasNothingToWrite(batch({ skippedCount: 30 }))).toBe(true);
    expect(hasNothingToWrite(batch({ updatedCount: 1 }))).toBe(false);
    expect(hasNothingToWrite(batch({ insertedCount: 1 }))).toBe(false);
    expect(hasNothingToWrite(batch({ invalidCount: 2 }))).toBe(false);
    expect(hasNothingToWrite(undefined)).toBe(false);
  });
});
