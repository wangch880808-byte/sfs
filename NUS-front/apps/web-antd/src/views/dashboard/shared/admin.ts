import type { StatisticsApi } from '#/api/edu/statistics';
import type { StatusTone } from '#/styles/theme/palette';

import { roundTo } from '#/views/edu/statistics/chart-kit';

import { ADMIN_LINKS } from './links';

/**
 * 管理员看板的数据整理（只整理现有接口的返回，不改口径）：成绩分布与需要关注的事项。
 */

export interface GradeBand {
  key: 'excellent' | 'good' | 'qualified' | 'unqualified';
  label: string;
  /** 占比（百分数，保留一位小数） */
  rate: number;
  /** 图表分类色（--nus-chart-*） */
  color: string;
}

/** 成绩分布：优秀、良好、合格、不合格的占比（后端给百分数） */
export function buildGradeBands(
  passRate?: Partial<StatisticsApi.PassRateRespVO>,
): GradeBand[] {
  return [
    {
      color: 'var(--nus-chart-2)',
      key: 'excellent',
      label: '优秀',
      rate: roundTo(Number(passRate?.excellentRate ?? 0)),
    },
    {
      color: 'var(--nus-chart-1)',
      key: 'good',
      label: '良好',
      rate: roundTo(Number(passRate?.goodRate ?? 0)),
    },
    {
      color: 'var(--nus-chart-3)',
      key: 'qualified',
      label: '合格',
      rate: roundTo(Number(passRate?.qualifiedRate ?? 0)),
    },
    {
      color: 'var(--nus-chart-4)',
      key: 'unqualified',
      label: '不合格',
      rate: roundTo(Number(passRate?.unqualifiedRate ?? 0)),
    },
  ];
}

export interface AttentionItem {
  key: string;
  tone: StatusTone;
  title: string;
  description: string;
  /** 处理的地方 */
  to?: string;
  action?: string;
}

/**
 * 需要关注的事项：待审批的公共课申请、已批改提交里不合格的比例。
 * 待审批数取管理员看板接口的 pendingPublicAuditCount，没有审批权限时为 null；
 * 没有读（没有权限）的项传 undefined 或 null，不出现。
 */
export function buildAdminAttention({
  passRate,
  pendingPublicAuditCount,
}: {
  passRate?: Partial<StatisticsApi.PassRateRespVO>;
  pendingPublicAuditCount?: null | number;
}): AttentionItem[] {
  const items: AttentionItem[] = [];
  const pendingAudits = Number(pendingPublicAuditCount ?? 0);
  if (pendingAudits > 0) {
    items.push({
      action: '去审批',
      description: '通过后，这门课作为公共课向全体教师开放。',
      key: 'public-course-audit',
      title: `${pendingAudits} 份公共课申请待审批`,
      to: ADMIN_LINKS.courseList,
      tone: 'waiting',
    });
  }
  const total = Number(passRate?.totalCount ?? 0);
  const unqualified = roundTo(Number(passRate?.unqualifiedRate ?? 0));
  if (total > 0 && unqualified > 0) {
    items.push({
      action: '教学问题研判',
      description: `已批改的 ${total} 份提交里，不合格的占比。`,
      key: 'unqualified-rate',
      title: `不合格占 ${unqualified}%`,
      to: ADMIN_LINKS.teachingIssue,
      tone: 'failed',
    });
  }
  return items;
}
