import type { AnalysisInsight } from './types';

import type { StatisticsApi } from '#/api/edu/statistics';

import {
  getInsightToneAtLeast,
  getInsightToneAtMost,
} from '#/utils/status-statistics';

import { average, clampPercent, formatPercent, roundTo } from '../chart-kit';

/**
 * 教学过程分析、教学问题研判的结论推导（V1.0.1.8）。
 * 没有已批改的任务时不能把“不合格占比为 0”当成“通过率 100%”：整体通过率记为 null，结论与得分都不下判断。
 */

type PassRate = Partial<StatisticsApi.PassRateRespVO>;

/** 已批改的提交总数：后端给了 totalCount 就用它，否则把四档人数加起来 */
export function getGradedTotal(passRate?: PassRate) {
  const total = Number(passRate?.totalCount);
  if (Number.isFinite(total)) {
    return Math.max(0, total);
  }
  return [
    passRate?.unqualifiedCount,
    passRate?.qualifiedCount,
    passRate?.goodCount,
    passRate?.excellentCount,
  ].reduce<number>((sum, count) => sum + (Number(count) || 0), 0);
}

/** 有可判断的数据：有任务成绩走势，且已批改的提交数大于 0 */
export function hasGradedData(
  passRate: PassRate | undefined,
  trendCount: number,
) {
  return trendCount > 0 && getGradedTotal(passRate) > 0;
}

/** 整体通过率（100 − 不合格占比）；没有已批改的任务时为 null */
export function getFullPassRate(
  passRate: PassRate | undefined,
  trendCount: number,
): null | number {
  if (!hasGradedData(passRate, trendCount)) {
    return null;
  }
  return roundTo(100 - Number(passRate?.unqualifiedRate ?? 0));
}

/** 没有已批改的任务时，过程结论只给这一条中性说明 */
export const NO_GRADED_INSIGHT: AnalysisInsight = {
  detail: '还没有已批改的任务，暂不判断达标情况。',
  title: '整体达标情况',
  tone: 'neutral',
};

/** 过程波动：均分变化在 3 分以内算平稳，上行为 positive，回落为 warning */
function getTrendTone(delta: number): AnalysisInsight['tone'] {
  if (Math.abs(delta) <= 3) return 'neutral';
  return delta > 3 ? 'positive' : 'warning';
}

/** 教学过程分析的过程结论 */
export function buildProcessInsights(input: {
  focusHomework?: { avgScore: number; homeworkName: string };
  fullPassRate: null | number;
  scoreGap: number;
  trendCount: number;
  trendDelta: number;
}): AnalysisInsight[] {
  const { focusHomework, fullPassRate, scoreGap, trendCount, trendDelta } =
    input;
  if (fullPassRate === null) {
    return [NO_GRADED_INSIGHT];
  }
  let trendDetail = '任务还不够多，暂时看不出稳定的走势。';
  if (trendCount >= 2) {
    trendDetail =
      trendDelta >= 0
        ? `均分整体上升 ${trendDelta} 分，教学节奏有助于任务达成。`
        : `均分回落 ${Math.abs(trendDelta)} 分，需要检查近期任务的节奏或难度。`;
  }
  const items: AnalysisInsight[] = [
    {
      title: '整体达标情况',
      tone: getInsightToneAtLeast(fullPassRate, 80, 65),
      detail:
        fullPassRate >= 80
          ? `当前课程整体通过率为 ${formatPercent(fullPassRate)}，达标情况稳定。`
          : `当前课程整体通过率为 ${formatPercent(fullPassRate)}，建议加强基础任务，并跟进未达标的学生。`,
    },
    {
      title: '过程波动',
      tone: getTrendTone(trendDelta),
      detail: trendDetail,
    },
    {
      title: '分层差距',
      tone: getInsightToneAtMost(scoreGap, 20, 35),
      detail:
        scoreGap <= 20
          ? `最高分与最低分跨任务差仅 ${scoreGap} 分，班级学习层次相对平稳。`
          : `当前最大成绩跨度达到 ${scoreGap} 分，建议加强分层辅导与任务反馈。`,
    },
  ];
  if (focusHomework) {
    items.push({
      title: '重点任务',
      tone: focusHomework.avgScore >= 75 ? 'neutral' : 'warning',
      detail: `均分最低的任务为「${focusHomework.homeworkName}」，当前均分 ${roundTo(focusHomework.avgScore)} 分，建议先检查它的说明、示例和批改标准。`,
    });
  }
  return items;
}

export interface IssueDimension {
  name: string;
  score: number;
}

/**
 * 教学问题研判的四个维度得分（公式沿用原页面）。没有已批改的任务（fullPassRate 为 null）时返回空列表，
 * 免得各维度的常数项（如节奏 74、分层 100）平均出一个看似正常的分数。
 */
export function buildIssueDimensions(input: {
  avgScore: number;
  fullPassRate: null | number;
  polarizationGap: number;
  qualityRate: number;
  trendDelta: number;
  volatility: number;
}): IssueDimension[] {
  const {
    avgScore,
    fullPassRate,
    polarizationGap,
    qualityRate,
    trendDelta,
    volatility,
  } = input;
  if (fullPassRate === null) {
    return [];
  }
  const pacing = clampPercent(
    74 + trendDelta * 4 - Math.max(0, volatility - 8) * 2.4,
  );
  const fit = clampPercent(
    avgScore * 0.45 + fullPassRate * 0.35 + qualityRate * 0.2,
  );
  const layered = clampPercent(100 - polarizationGap * 1.6);
  const safeguard = clampPercent(
    fullPassRate * 0.75 + (100 - Math.max(0, 65 - avgScore) * 1.4) * 0.25,
  );
  return [
    { name: '教学节奏', score: roundTo(pacing) },
    { name: '任务适配', score: roundTo(fit) },
    { name: '分层支持', score: roundTo(layered) },
    { name: '结果保障', score: roundTo(safeguard) },
  ];
}

/** 综合研判得分：各维度平均；没有维度得分时为 null */
export function getIssueOverallScore(dimensions: IssueDimension[]) {
  if (dimensions.length === 0) {
    return null;
  }
  return roundTo(average(dimensions.map((item) => item.score)));
}

/** 教学问题研判的问题结论；没有已批改的任务时为空，页面显示空状态 */
export function buildIssueInsights(input: {
  focusTask?: { avgScore: number; homeworkName: string; riskScore: number };
  fullPassRate: null | number;
  polarizationGap: number;
  trendCount: number;
  trendDelta: number;
}): AnalysisInsight[] {
  const { focusTask, fullPassRate, polarizationGap, trendCount, trendDelta } =
    input;
  if (fullPassRate === null) {
    return [];
  }
  let momentumDetail = '任务还不够多，暂不判断成绩走势。';
  if (trendCount >= 2) {
    momentumDetail =
      trendDelta >= 0
        ? `近期任务均分上升 ${trendDelta} 分，课程推进节奏总体有效。`
        : `近期任务均分回落 ${Math.abs(trendDelta)} 分，需要检查任务难度是否循序渐进、课堂讲评是否跟上。`;
  }
  const items: AnalysisInsight[] = [
    {
      title: '达标保障',
      tone: getInsightToneAtLeast(fullPassRate, 80, 65),
      detail:
        fullPassRate >= 80
          ? `整体通过率达到 ${formatPercent(fullPassRate)}，课程基础较稳。`
          : `整体通过率为 ${formatPercent(fullPassRate)}，需要跟进未提交的学生，并巩固基础能力。`,
    },
    {
      title: '成绩走势',
      tone: getInsightToneAtLeast(trendDelta, 2, -2),
      detail: momentumDetail,
    },
    {
      title: '班内差距',
      tone: getInsightToneAtMost(polarizationGap, 18, 28),
      detail:
        polarizationGap <= 18
          ? `高低分差平均约 ${polarizationGap} 分，班级内部差距可控。`
          : `高低分差平均约 ${polarizationGap} 分，班级内部差距较大，建议补充分层任务与示范样例。`,
    },
  ];
  if (focusTask) {
    items.push({
      title: '重点问题任务',
      tone: focusTask.riskScore >= 45 ? 'warning' : 'neutral',
      detail: `「${focusTask.homeworkName}」当前风险热度 ${focusTask.riskScore}，均分 ${roundTo(focusTask.avgScore)} 分，建议先检查任务说明与达标标准。`,
    });
  }
  return items;
}
