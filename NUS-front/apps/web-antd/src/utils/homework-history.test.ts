import { describe, expect, it } from 'vitest';

import {
  getHomeworkHistoryCommentLabel,
  getHomeworkHistoryEventLabel,
  getHomeworkHistoryScoreLabel,
  getHomeworkRecordStatusLabel,
} from './homework-history';

describe('提交与评分轨迹文案', () => {
  it.each([
    ['SUBMIT', '提交'],
    ['RESUBMIT', '重新提交'],
    ['CORRECT', '批改'],
    ['REDO', '打回重做'],
    ['REDO_DEADLINE', '调整重做截止时间'],
  ])('映射事件 %s', (type, label) => {
    expect(getHomeworkHistoryEventLabel(type)).toBe(label);
  });

  it.each([
    [0, '未提交'],
    [1, '待批改'],
    [2, '已批改'],
    [3, '已打回'],
    [4, '已重新提交'],
  ])('映射状态 %s', (status, label) => {
    expect(getHomeworkRecordStatusLabel(status)).toBe(label);
  });

  it('对未知值和缺失状态使用安全文案', () => {
    expect(getHomeworkHistoryEventLabel('FUTURE_EVENT')).toBe('其他记录');
    expect(getHomeworkRecordStatusLabel(99)).toBe('未知状态');
    expect(getHomeworkRecordStatusLabel(null)).toBe('未知状态');
  });

  it('打回事件中的保留分数标注为原成绩', () => {
    expect(getHomeworkHistoryScoreLabel('REDO')).toBe('原成绩');
    expect(getHomeworkHistoryScoreLabel('REDO_DEADLINE')).toBe('原成绩');
    expect(getHomeworkHistoryScoreLabel('RESUBMIT')).toBe('原成绩');
    expect(getHomeworkHistoryScoreLabel('CORRECT')).toBe('成绩');
    expect(getHomeworkHistoryCommentLabel('RESUBMIT')).toBe('原评语');
    expect(getHomeworkHistoryCommentLabel('CORRECT')).toBe('评语');
  });
});
