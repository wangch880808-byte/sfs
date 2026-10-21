import { describe, expect, it } from 'vitest';

import {
  GRADING_STRATEGY_OPTIONS,
  resolveGradingStrategy,
  resolveGradingStrategyConfig,
} from './data';

describe('批改方式', () => {
  it('只提供人工批改与 AI 初评 + 教师复核两种', () => {
    expect(GRADING_STRATEGY_OPTIONS.map((item) => item.value)).toEqual([
      'manual',
      'assist',
    ]);
  });

  it('早期 autoGrade=1、aiReviewMode=0 的任务回显为 AI 初评 + 教师复核', () => {
    expect(resolveGradingStrategy(1, 0)).toBe('assist');
    expect(resolveGradingStrategy(1, 1)).toBe('assist');
    expect(resolveGradingStrategy(0, 1)).toBe('manual');
    expect(resolveGradingStrategy(undefined, undefined)).toBe('manual');
  });

  it('保存时 AI 初评总是同时打开 autoGrade 与 aiReviewMode', () => {
    expect(resolveGradingStrategyConfig('assist')).toEqual({
      aiReviewMode: 1,
      autoGrade: 1,
    });
    expect(resolveGradingStrategyConfig('auto')).toEqual({
      aiReviewMode: 0,
      autoGrade: 0,
    });
    expect(resolveGradingStrategyConfig('manual')).toEqual({
      aiReviewMode: 0,
      autoGrade: 0,
    });
  });
});
