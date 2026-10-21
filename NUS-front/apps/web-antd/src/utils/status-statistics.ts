import type { StatusMeta } from './status';

import type { StatusTone } from '#/styles/theme/palette';

import { getHomeworkRecordStatusMeta, HOMEWORK_RECORD_STATUS } from './status';

/**
 * 学情分析页的状态呈现（V1.0.1.6 界面规范）：教学过程分析、教学问题研判、单项能力评测。
 * 类别沿用 utils/status.ts：表现稳定与达标为 done，需要注意为 waiting，未达标与高风险为 failed，其余 neutral。
 * 各页的分档阈值原样搬过来，只把原来的标签颜色换成状态类别。
 */

/** 分析结论的语气：positive 表现稳定、neutral 一般、warning 需要注意 */
export type InsightTone = 'neutral' | 'positive' | 'warning';

const INSIGHT_STATUS_TONE: Record<InsightTone, StatusTone> = {
  neutral: 'neutral',
  positive: 'done',
  warning: 'waiting',
};

/** 分析结论（过程洞察、问题结论、学习提示）前面的圆点 */
export function getInsightStatusTone(tone: InsightTone): StatusTone {
  return INSIGHT_STATUS_TONE[tone];
}

/** 越高越好的指标（通过率、达标率、均分变化）：≥ good 为 positive，≥ fair 为 neutral，其余 warning */
export function getInsightToneAtLeast(
  value: number,
  good: number,
  fair: number,
): InsightTone {
  if (value >= good) {
    return 'positive';
  }
  return value >= fair ? 'neutral' : 'warning';
}

/** 越低越好的指标（成绩跨度、头尾差）：≤ good 为 positive，≤ fair 为 neutral，其余 warning */
export function getInsightToneAtMost(
  value: number,
  good: number,
  fair: number,
): InsightTone {
  if (value <= good) {
    return 'positive';
  }
  return value <= fair ? 'neutral' : 'warning';
}

/**
 * 单项能力评测“相关任务”的状态：提交记录的状态，已批改的再按是否达标细分。
 * 文字按术语表写（打回写「已打回」），类别取学生视角的提交记录状态；
 * 已批改但没有达标结论（没有成绩）时用提交记录的“已批改”，没有记录为未提交。
 */
export function getSkillTaskStatusMeta(task: {
  qualified?: boolean | null;
  recordStatus?: null | number;
}): StatusMeta {
  const { qualified, recordStatus } = task;
  const tone = (status: number) =>
    getHomeworkRecordStatusMeta(status, 'student').tone;
  switch (recordStatus) {
    case HOMEWORK_RECORD_STATUS.CORRECTED: {
      if (qualified === null || qualified === undefined) {
        return getHomeworkRecordStatusMeta(recordStatus, 'student');
      }
      return qualified
        ? { label: '已评定达标', tone: 'done' }
        : { label: '已评定未达标', tone: 'failed' };
    }
    case HOMEWORK_RECORD_STATUS.REDO: {
      return { label: '已打回', tone: tone(recordStatus) };
    }
    case HOMEWORK_RECORD_STATUS.RESUBMITTED: {
      return { label: '已重新提交待批改', tone: tone(recordStatus) };
    }
    case HOMEWORK_RECORD_STATUS.SUBMITTED: {
      return { label: '已提交待批改', tone: tone(recordStatus) };
    }
    default: {
      return {
        label: '未提交',
        tone: tone(HOMEWORK_RECORD_STATUS.NOT_SUBMITTED),
      };
    }
  }
}

/** 单项能力评测的总体达标情况：≥70 总体稳定，≥60 基本达标，其余仍需补强；还没有评定结果为待评定 */
export function getQualifiedLevelMeta(
  rate: null | number | undefined,
): StatusMeta {
  if (rate === null || rate === undefined) {
    return { label: '待评定', tone: 'neutral' };
  }
  if (rate >= 70) {
    return { label: '总体稳定', tone: 'done' };
  }
  if (rate >= 60) {
    return { label: '基本达标', tone: 'neutral' };
  }
  return { label: '仍需补强', tone: 'waiting' };
}

/** 指标达标率：≥80 为优势、低于 60 需要补强（与后端优势、薄弱指标的口径一致），其余 neutral */
export function getQualifiedRateTone(
  rate: null | number | undefined,
): StatusTone {
  if (rate === null || rate === undefined) {
    return 'neutral';
  }
  if (rate >= 80) {
    return 'done';
  }
  return rate < 60 ? 'waiting' : 'neutral';
}

/** 教学过程分析的任务均分：≥75 稳定，≥65 一般，其余需要跟进 */
export function getAverageScoreTone(avgScore: number): StatusTone {
  if (avgScore >= 75) {
    return 'done';
  }
  return avgScore >= 65 ? 'neutral' : 'waiting';
}

/** 教学问题研判的综合风险：综合研判得分 ≥80 低风险，≥68 中风险，其余高风险 */
export function getRiskLevelMeta(overallScore: number): StatusMeta {
  if (overallScore >= 80) {
    return { label: '低风险', tone: 'done' };
  }
  if (overallScore >= 68) {
    return { label: '中风险', tone: 'waiting' };
  }
  return { label: '高风险', tone: 'failed' };
}

/** 教学问题研判的任务风险热度：≥55 高，≥40 需要注意，其余 neutral */
export function getTaskRiskTone(riskScore: number): StatusTone {
  if (riskScore >= 55) {
    return 'failed';
  }
  return riskScore >= 40 ? 'waiting' : 'neutral';
}
