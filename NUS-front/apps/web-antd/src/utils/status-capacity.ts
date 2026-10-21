import type { StatusMeta } from './status';

/**
 * 学生能力分析与实训报告的状态呈现（V1.0.1.6 界面规范）。类别沿用 utils/status.ts：
 * 待评定是在等教师批改（running），已达标 done、未达标 failed；可获证 done，尚未达到等学生继续努力（waiting）。
 */

const PENDING: StatusMeta = { label: '待评定', tone: 'running' };

/** 是否达标；还没有已评定的任务时为 null（待评定） */
export function getQualifiedStatusMeta(
  qualified: boolean | null | undefined,
): StatusMeta {
  if (qualified === null || qualified === undefined) {
    return PENDING;
  }
  return qualified
    ? { label: '已达标', tone: 'done' }
    : { label: '未达标', tone: 'failed' };
}

/** 单项指标的达标率还没有评定时（null） */
export function getPendingEvaluationMeta(): StatusMeta {
  return PENDING;
}

/** 是否达到证书的获证阈值；后端没给出判定（null）时不显示 */
export function getCertificateEligibilityMeta(
  eligible: boolean | null | undefined,
): null | StatusMeta {
  if (eligible === null || eligible === undefined) {
    return null;
  }
  return eligible
    ? { label: '已达到获证阈值', tone: 'done' }
    : { label: '尚未达到获证阈值', tone: 'waiting' };
}

export type SkillHighlight = 'strength' | 'weakness';

const SKILL_HIGHLIGHT_META: Record<SkillHighlight, StatusMeta> = {
  strength: { label: '优势', tone: 'done' },
  weakness: { label: '薄弱', tone: 'failed' },
};

/** 优势与薄弱的标签 */
export function getSkillHighlightMeta(
  highlight: null | SkillHighlight | undefined,
): null | StatusMeta {
  return highlight ? SKILL_HIGHLIGHT_META[highlight] : null;
}

/** 能力分析页按达标率划分：80% 及以上为优势，60% 以下为薄弱，其余与未评定不标 */
export function getRateHighlight(
  rate: null | number | undefined,
): null | SkillHighlight {
  if (rate === null || rate === undefined) {
    return null;
  }
  if (rate >= 80) {
    return 'strength';
  }
  if (rate < 60) {
    return 'weakness';
  }
  return null;
}

/** 实训报告按后端给出的优势、薄弱名单标注；同时出现时按优势显示 */
export function getListedHighlight(
  name: string,
  strengths: readonly string[] = [],
  weaknesses: readonly string[] = [],
): null | SkillHighlight {
  if (strengths.includes(name)) {
    return 'strength';
  }
  if (weaknesses.includes(name)) {
    return 'weakness';
  }
  return null;
}
