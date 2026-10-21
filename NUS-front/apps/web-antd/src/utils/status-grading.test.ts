import { describe, expect, it } from 'vitest';

import {
  getAiRiskFlagMeta,
  hasVisibleScoreStatus,
  isAiBatchTaskFailed,
  isAiPreReviewPending,
  isCorrectableStatus,
  isPendingReviewStatus,
  isPreviousScoreStatus,
  isRedoStatus,
} from './status-grading';

describe('初评草案的风险标记', () => {
  it('低置信度与必须人工复核要逐项核对，标为 failed', () => {
    expect(getAiRiskFlagMeta('low_confidence')).toEqual({
      label: '低置信度',
      tone: 'failed',
    });
    expect(getAiRiskFlagMeta('manual_review_required')).toEqual({
      label: '必须人工复核',
      tone: 'failed',
    });
  });

  it('其余标记提醒注意，标为 waiting', () => {
    for (const flag of [
      'audio_transcript_empty',
      'invalid_model_risk_flags',
      'no_effective_evidence',
      'video_manual_review_only',
    ]) {
      expect(getAiRiskFlagMeta(flag).tone).toBe('waiting');
    }
    expect(getAiRiskFlagMeta('video_manual_review_only').label).toBe(
      '视频只能人工批改',
    );
  });

  it('不认识的标记不显示原值', () => {
    expect(getAiRiskFlagMeta('some_new_flag')).toEqual({
      label: '其他风险标记',
      tone: 'waiting',
    });
    expect(getAiRiskFlagMeta(undefined).label).toBe('其他风险标记');
    expect(getAiRiskFlagMeta('').label).toBe('其他风险标记');
  });
});

describe('批改工作台的提交状态判断', () => {
  it('待批改含首次提交与重新提交；可批改再加已批改', () => {
    expect([0, 1, 2, 3, 4].filter((s) => isPendingReviewStatus(s))).toEqual([
      1, 4,
    ]);
    expect([0, 1, 2, 3, 4].filter((s) => isCorrectableStatus(s))).toEqual([
      1, 2, 4,
    ]);
    expect(isCorrectableStatus(undefined)).toBe(false);
    expect(isRedoStatus(3)).toBe(true);
  });

  it('分数只在已批改、已打回、已重新提交时显示，后两者是原成绩', () => {
    expect([0, 1, 2, 3, 4].filter((s) => hasVisibleScoreStatus(s))).toEqual([
      2, 3, 4,
    ]);
    expect([0, 1, 2, 3, 4].filter((s) => isPreviousScoreStatus(s))).toEqual([
      3, 4,
    ]);
  });

  it('aI 初评排队、处理中要轮询；批量任务部分失败与失败都算失败', () => {
    expect(
      [0, 1, 2, 3, 4, 5, 6].filter((s) => isAiPreReviewPending(s)),
    ).toEqual([0, 1]);
    expect(isAiPreReviewPending(null)).toBe(false);
    expect([0, 1, 2, 3].filter((s) => isAiBatchTaskFailed(s))).toEqual([2, 3]);
  });
});
