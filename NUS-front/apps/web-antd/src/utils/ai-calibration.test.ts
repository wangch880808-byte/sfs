import type { AiReviewApi } from '#/api/edu/ai-review';

import { describe, expect, it } from 'vitest';

import { getAiCalibrationDisplay } from './ai-calibration';

const base: AiReviewApi.CalibrationRespVO = {
  scope: 'TASK',
  sampleCount: 12,
  strictCount: 0,
  normalCount: 0,
  looseCount: 0,
  averageDeviation: -3.5,
  fullScore: 100,
  direction: 'STRICTER',
  hint: '',
};

describe('aI calibration copy', () => {
  it('maps stricter and looser directions using the deviation sign', () => {
    expect(getAiCalibrationDisplay(base).text).toContain(
      '当前任务 12 条样本，平均比 AI 低 3.5 分',
    );
    expect(
      getAiCalibrationDisplay({
        ...base,
        scope: 'COURSE',
        direction: 'LOOSER',
        averageDeviation: 2.25,
      }).text,
    ).toContain('同一课程 12 条样本，平均比 AI 高 2.3 分');
  });

  it('maps insufficient and consistent states', () => {
    expect(
      getAiCalibrationDisplay({
        ...base,
        direction: 'INSUFFICIENT',
        scope: 'NONE',
      }).text,
    ).toContain('样本不足，暂不校准');
    expect(
      getAiCalibrationDisplay({ ...base, direction: 'NONE' }).text,
    ).toContain('没有明显的从严或从宽倾向，无需校准');
  });

  it('reports the actual deviation sign when feedback sets the direction', () => {
    expect(
      getAiCalibrationDisplay({
        ...base,
        averageDeviation: 1,
      }).text,
    ).toContain('平均比 AI 高 1.0 分');
  });
});
