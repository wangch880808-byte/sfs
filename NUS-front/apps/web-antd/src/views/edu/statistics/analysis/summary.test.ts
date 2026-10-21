import { describe, expect, it } from 'vitest';

import {
  buildIssueDimensions,
  buildIssueInsights,
  buildProcessInsights,
  getFullPassRate,
  getGradedTotal,
  getIssueOverallScore,
  hasGradedData,
  NO_GRADED_INSIGHT,
} from './summary';

const EMPTY_PASS_RATE = {
  excellentCount: 0,
  excellentRate: 0,
  goodCount: 0,
  goodRate: 0,
  qualifiedCount: 0,
  qualifiedRate: 0,
  totalCount: 0,
  unqualifiedCount: 0,
  unqualifiedRate: 0,
};

describe('学情分析：已批改数据', () => {
  it('已批改总数取 totalCount，没给时把四档人数相加', () => {
    expect(getGradedTotal({ totalCount: 12 })).toBe(12);
    expect(getGradedTotal({ goodCount: 2, unqualifiedCount: 1 })).toBe(3);
    expect(getGradedTotal()).toBe(0);
  });

  it('没有成绩走势或已批改总数为 0 时没有数据，整体通过率为 null', () => {
    expect(hasGradedData(EMPTY_PASS_RATE, 3)).toBe(false);
    expect(hasGradedData({ totalCount: 5 }, 0)).toBe(false);
    expect(getFullPassRate(EMPTY_PASS_RATE, 3)).toBeNull();
    expect(getFullPassRate(undefined, 0)).toBeNull();
  });

  it('有数据时整体通过率为 100 减不合格占比', () => {
    expect(getFullPassRate({ totalCount: 8, unqualifiedRate: 12.5 }, 2)).toBe(
      87.5,
    );
    expect(getFullPassRate({ totalCount: 8, unqualifiedRate: 0 }, 1)).toBe(100);
  });
});

describe('教学过程分析：过程结论', () => {
  it('没有已批改的任务时只给一条中性说明，不写通过率 100%', () => {
    const items = buildProcessInsights({
      fullPassRate: getFullPassRate(EMPTY_PASS_RATE, 0),
      scoreGap: 0,
      trendCount: 0,
      trendDelta: 0,
    });
    expect(items).toEqual([NO_GRADED_INSIGHT]);
    expect(items[0]).toMatchObject({
      detail: '还没有已批改的任务，暂不判断达标情况。',
      tone: 'neutral',
    });
    expect(items.some((item) => item.detail.includes('100%'))).toBe(false);
  });

  it('有数据时按通过率、走势、分层差距与重点任务给结论', () => {
    const items = buildProcessInsights({
      focusHomework: { avgScore: 62, homeworkName: '接口测试' },
      fullPassRate: 60,
      scoreGap: 40,
      trendCount: 3,
      trendDelta: -5,
    });
    expect(items.map((item) => [item.title, item.tone])).toEqual([
      ['整体达标情况', 'warning'],
      ['过程波动', 'warning'],
      ['分层差距', 'warning'],
      ['重点任务', 'warning'],
    ]);
    expect(items[0]?.detail).toContain('60%');
  });
});

describe('教学问题研判：得分与结论', () => {
  it('没有已批改的任务时没有维度得分、综合得分为 null、结论为空', () => {
    const dimensions = buildIssueDimensions({
      avgScore: 0,
      fullPassRate: null,
      polarizationGap: 0,
      qualityRate: 0,
      trendDelta: 0,
      volatility: 0,
    });
    expect(dimensions).toEqual([]);
    expect(getIssueOverallScore(dimensions)).toBeNull();
    expect(
      buildIssueInsights({
        fullPassRate: null,
        polarizationGap: 0,
        trendCount: 0,
        trendDelta: 0,
      }),
    ).toEqual([]);
  });

  it('有数据时按原公式算四个维度，综合得分取平均', () => {
    const dimensions = buildIssueDimensions({
      avgScore: 80,
      fullPassRate: 90,
      polarizationGap: 10,
      qualityRate: 50,
      trendDelta: 2,
      volatility: 4,
    });
    expect(dimensions).toEqual([
      { name: '教学节奏', score: 82 },
      { name: '任务适配', score: 77.5 },
      { name: '分层支持', score: 84 },
      { name: '结果保障', score: 92.5 },
    ]);
    expect(getIssueOverallScore(dimensions)).toBe(84);
  });

  it('有数据时的结论包含达标保障、成绩走势与班内差距', () => {
    const items = buildIssueInsights({
      focusTask: { avgScore: 55, homeworkName: '部署上线', riskScore: 50 },
      fullPassRate: 85,
      polarizationGap: 30,
      trendCount: 1,
      trendDelta: 0,
    });
    expect(items.map((item) => [item.title, item.tone])).toEqual([
      ['达标保障', 'positive'],
      ['成绩走势', 'neutral'],
      ['班内差距', 'warning'],
      ['重点问题任务', 'warning'],
    ]);
    expect(items[1]?.detail).toBe('任务还不够多，暂不判断成绩走势。');
  });
});
