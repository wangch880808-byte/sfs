import { describe, expect, it } from 'vitest';

import { buildAdminAttention, buildGradeBands } from './admin';
import { ADMIN_LINKS } from './links';

describe('管理员看板：成绩分布', () => {
  it('四档按优秀、良好、合格、不合格排列，占比保留一位小数，颜色取图表分类色', () => {
    const bands = buildGradeBands({
      excellentRate: 33.333,
      goodRate: 33.333,
      qualifiedRate: 33.334,
      totalCount: 3,
      unqualifiedRate: 0,
    });
    expect(bands.map((band) => [band.label, band.rate])).toEqual([
      ['优秀', 33.3],
      ['良好', 33.3],
      ['合格', 33.3],
      ['不合格', 0],
    ]);
    expect(
      bands.every((band) => band.color.startsWith('var(--nus-chart-')),
    ).toBe(true);
  });

  it('没有数据时各档为 0', () => {
    expect(buildGradeBands().map((band) => band.rate)).toEqual([0, 0, 0, 0]);
  });
});

describe('管理员看板：需要关注', () => {
  it('有待审批的公共课申请与不合格时列出，并给出处理的地方', () => {
    const items = buildAdminAttention({
      passRate: { totalCount: 8, unqualifiedRate: 12.5 },
      pendingPublicAuditCount: 2,
    });
    expect(items).toEqual([
      expect.objectContaining({
        action: '去审批',
        title: '2 份公共课申请待审批',
        to: ADMIN_LINKS.courseList,
        tone: 'waiting',
      }),
      expect.objectContaining({
        description: '已批改的 8 份提交里，不合格的占比。',
        title: '不合格占 12.5%',
        to: ADMIN_LINKS.teachingIssue,
        tone: 'failed',
      }),
    ]);
  });

  it('没有需要关注的、没读到的都不列', () => {
    expect(
      buildAdminAttention({
        passRate: { totalCount: 1, unqualifiedRate: 0 },
        pendingPublicAuditCount: 0,
      }),
    ).toEqual([]);
    expect(
      buildAdminAttention({ passRate: { totalCount: 0, unqualifiedRate: 50 } }),
    ).toEqual([]);
    expect(buildAdminAttention({})).toEqual([]);
  });

  it('待审批数取看板接口的 pendingPublicAuditCount：为 null（没有审批权限）时不列，大于 0 时列出', () => {
    expect(buildAdminAttention({ pendingPublicAuditCount: null })).toEqual([]);
    expect(buildAdminAttention({ pendingPublicAuditCount: 0 })).toEqual([]);
    expect(buildAdminAttention({ pendingPublicAuditCount: 3 })).toEqual([
      expect.objectContaining({
        key: 'public-course-audit',
        title: '3 份公共课申请待审批',
        to: ADMIN_LINKS.courseList,
      }),
    ]);
  });
});
