import type { StudentCapacityApi } from '#/api/edu/student/capacity';
import type { StatusMeta } from '#/utils/status';
import type { CapacityChartItem } from '#/views/edu/statistics/chart-kit';

import { getQualifiedStatusMeta } from '#/utils/status-capacity';

/**
 * 学生能力分析与实训报告的口径（V1.0.1.8）：任务计数统计全部任务，能力画像（雷达图、综合达标率、获证）
 * 只统计关联了能力指标的任务。已评定但没有关联指标的任务要写明不计入能力画像，不再显示成“待评定”。
 */

type AnalysisLike = Pick<
  StudentCapacityApi.CapacityAnalysis,
  'overallRate' | 'qualified' | 'unindicatedEvaluatedCount'
>;

/** 已评定、但没有关联能力指标的任务数；后端没给时按 0 */
export function getUnindicatedCount(analysis?: Partial<AnalysisLike>) {
  const count = Number(analysis?.unindicatedEvaluatedCount ?? 0);
  return Number.isFinite(count) && count > 0 ? Math.floor(count) : 0;
}

/** 未关联指标任务的说明；没有这类任务时为空 */
export function getUnindicatedNote(count: number) {
  return count > 0
    ? `有 ${count} 个已评定任务未关联能力指标，不计入能力画像。`
    : '';
}

/**
 * 综合达标率卡片：有达标率时显示百分比；还没有达标率、但有未关联指标的已评定任务时显示“—”并写明原因；
 * 都没有时仍为待评定。
 */
export function getOverallRateView(analysis?: Partial<AnalysisLike>): {
  meta: string;
  value: string;
} {
  const rate = analysis?.overallRate;
  if (rate !== null && rate !== undefined) {
    return { meta: '', value: `${rate}%` };
  }
  const count = getUnindicatedCount(analysis);
  if (count > 0) {
    return { meta: getUnindicatedNote(count), value: '—' };
  }
  return { meta: '', value: '待评定' };
}

/** 达标状态：还没有能力画像、但已有未关联指标的已评定任务时，不显示“待评定” */
export function getCapacityQualifiedMeta(
  analysis?: Partial<AnalysisLike>,
): StatusMeta {
  const qualified = analysis?.qualified;
  if (
    (qualified === null || qualified === undefined) &&
    getUnindicatedCount(analysis) > 0
  ) {
    return { label: '暂无能力画像', tone: 'neutral' };
  }
  return getQualifiedStatusMeta(qualified);
}

/** 能力分布图的数据：只取已有达标率的指标 */
export function toCapacityChartItems(
  list: ReadonlyArray<{ indicatorName: string; qualifiedRate: null | number }>,
): CapacityChartItem[] {
  return list
    .filter(
      (item): item is { indicatorName: string; qualifiedRate: number } =>
        item.qualifiedRate !== null && item.qualifiedRate !== undefined,
    )
    .map((item) => ({ name: item.indicatorName, value: item.qualifiedRate }));
}
