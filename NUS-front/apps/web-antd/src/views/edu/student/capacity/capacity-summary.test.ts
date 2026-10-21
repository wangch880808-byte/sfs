import { describe, expect, it } from 'vitest';

import {
  getCapacityQualifiedMeta,
  getOverallRateView,
  getUnindicatedCount,
  getUnindicatedNote,
  toCapacityChartItems,
} from './capacity-summary';

describe('学生能力分析：未关联能力指标的任务', () => {
  it('数量按后端给的值，没给或不合法时为 0', () => {
    expect(getUnindicatedCount({ unindicatedEvaluatedCount: 2 })).toBe(2);
    expect(getUnindicatedCount({})).toBe(0);
    expect(getUnindicatedCount(undefined)).toBe(0);
    expect(getUnindicatedCount({ unindicatedEvaluatedCount: -1 })).toBe(0);
  });

  it('有这类任务时写明不计入能力画像，没有时不说明', () => {
    expect(getUnindicatedNote(3)).toBe(
      '有 3 个已评定任务未关联能力指标，不计入能力画像。',
    );
    expect(getUnindicatedNote(0)).toBe('');
  });

  it('综合达标率为空但有这类任务时显示说明，不再写待评定', () => {
    expect(
      getOverallRateView({ overallRate: null, unindicatedEvaluatedCount: 2 }),
    ).toEqual({
      meta: '有 2 个已评定任务未关联能力指标，不计入能力画像。',
      value: '—',
    });
    expect(getOverallRateView({ overallRate: null })).toEqual({
      meta: '',
      value: '待评定',
    });
    expect(
      getOverallRateView({ overallRate: 75, unindicatedEvaluatedCount: 2 }),
    ).toEqual({ meta: '', value: '75%' });
  });

  it('达标状态：没有能力画像但有这类任务时显示暂无能力画像', () => {
    expect(
      getCapacityQualifiedMeta({
        qualified: null,
        unindicatedEvaluatedCount: 1,
      }),
    ).toEqual({ label: '暂无能力画像', tone: 'neutral' });
    expect(getCapacityQualifiedMeta({ qualified: null }).label).toBe('待评定');
    expect(
      getCapacityQualifiedMeta({
        qualified: true,
        unindicatedEvaluatedCount: 1,
      }).label,
    ).toBe('已达标');
  });

  it('能力分布图只取已有达标率的指标', () => {
    expect(
      toCapacityChartItems([
        { indicatorName: '需求分析', qualifiedRate: 50 },
        { indicatorName: '编码实现', qualifiedRate: null },
      ]),
    ).toEqual([{ name: '需求分析', value: 50 }]);
  });
});
