import { describe, expect, it } from 'vitest';

import { HOMEWORK_RECORD_STATUS } from './status';
import {
  getAverageScoreTone,
  getInsightStatusTone,
  getInsightToneAtLeast,
  getInsightToneAtMost,
  getQualifiedLevelMeta,
  getQualifiedRateTone,
  getRiskLevelMeta,
  getSkillTaskStatusMeta,
  getTaskRiskTone,
} from './status-statistics';

describe('单项能力评测的相关任务状态', () => {
  it('提交后等教师批改，打回等学生重做', () => {
    expect(
      getSkillTaskStatusMeta({
        recordStatus: HOMEWORK_RECORD_STATUS.SUBMITTED,
      }),
    ).toEqual({ label: '已提交待批改', tone: 'running' });
    expect(
      getSkillTaskStatusMeta({
        recordStatus: HOMEWORK_RECORD_STATUS.RESUBMITTED,
      }),
    ).toEqual({ label: '已重新提交待批改', tone: 'running' });
    expect(
      getSkillTaskStatusMeta({ recordStatus: HOMEWORK_RECORD_STATUS.REDO }),
    ).toEqual({ label: '已打回', tone: 'waiting' });
  });

  it('已批改的按是否达标细分，没有达标结论时显示已批改', () => {
    expect(
      getSkillTaskStatusMeta({
        qualified: true,
        recordStatus: HOMEWORK_RECORD_STATUS.CORRECTED,
      }),
    ).toEqual({ label: '已评定达标', tone: 'done' });
    expect(
      getSkillTaskStatusMeta({
        qualified: false,
        recordStatus: HOMEWORK_RECORD_STATUS.CORRECTED,
      }),
    ).toEqual({ label: '已评定未达标', tone: 'failed' });
    expect(
      getSkillTaskStatusMeta({
        qualified: null,
        recordStatus: HOMEWORK_RECORD_STATUS.CORRECTED,
      }),
    ).toEqual({ label: '已批改', tone: 'done' });
  });

  it('没有提交记录、未提交与未知状态都显示未提交', () => {
    for (const recordStatus of [null, undefined, 0, 9]) {
      expect(getSkillTaskStatusMeta({ recordStatus })).toEqual({
        label: '未提交',
        tone: 'neutral',
      });
    }
  });

  it('只有已批改的记录才看达标结论', () => {
    expect(
      getSkillTaskStatusMeta({
        qualified: true,
        recordStatus: HOMEWORK_RECORD_STATUS.SUBMITTED,
      }).label,
    ).toBe('已提交待批改');
  });
});

describe('学情分析的分档', () => {
  it('分析结论的语气换成状态类别', () => {
    expect(getInsightStatusTone('positive')).toBe('done');
    expect(getInsightStatusTone('neutral')).toBe('neutral');
    expect(getInsightStatusTone('warning')).toBe('waiting');
  });

  it('越高越好的指标：到 good 为 positive，到 fair 为 neutral，边界值算在上一档', () => {
    expect(getInsightToneAtLeast(80, 80, 65)).toBe('positive');
    expect(getInsightToneAtLeast(65, 80, 65)).toBe('neutral');
    expect(getInsightToneAtLeast(64.9, 80, 65)).toBe('warning');
    // 均分变化可以是负数
    expect(getInsightToneAtLeast(-2, 2, -2)).toBe('neutral');
    expect(getInsightToneAtLeast(-2.1, 2, -2)).toBe('warning');
  });

  it('越低越好的指标：不超过 good 为 positive，不超过 fair 为 neutral', () => {
    expect(getInsightToneAtMost(20, 20, 35)).toBe('positive');
    expect(getInsightToneAtMost(35, 20, 35)).toBe('neutral');
    expect(getInsightToneAtMost(35.1, 20, 35)).toBe('warning');
  });

  it('总体达标情况：70、60 两档，没有评定结果为待评定', () => {
    expect(getQualifiedLevelMeta(null)).toEqual({
      label: '待评定',
      tone: 'neutral',
    });
    expect(getQualifiedLevelMeta(70)).toEqual({
      label: '总体稳定',
      tone: 'done',
    });
    expect(getQualifiedLevelMeta(60)).toEqual({
      label: '基本达标',
      tone: 'neutral',
    });
    expect(getQualifiedLevelMeta(59.9)).toEqual({
      label: '仍需补强',
      tone: 'waiting',
    });
  });

  it('指标达标率：80 以上为优势，低于 60 需要补强', () => {
    expect(getQualifiedRateTone(80)).toBe('done');
    expect(getQualifiedRateTone(60)).toBe('neutral');
    expect(getQualifiedRateTone(59.9)).toBe('waiting');
    expect(getQualifiedRateTone(null)).toBe('neutral');
  });

  it('任务均分：75、65 两档', () => {
    expect(getAverageScoreTone(75)).toBe('done');
    expect(getAverageScoreTone(65)).toBe('neutral');
    expect(getAverageScoreTone(64.9)).toBe('waiting');
  });

  it('综合风险：80、68 两档', () => {
    expect(getRiskLevelMeta(80)).toEqual({ label: '低风险', tone: 'done' });
    expect(getRiskLevelMeta(68)).toEqual({ label: '中风险', tone: 'waiting' });
    expect(getRiskLevelMeta(67.9)).toEqual({
      label: '高风险',
      tone: 'failed',
    });
  });

  it('任务风险热度：55、40 两档', () => {
    expect(getTaskRiskTone(55)).toBe('failed');
    expect(getTaskRiskTone(40)).toBe('waiting');
    expect(getTaskRiskTone(39.9)).toBe('neutral');
  });
});
