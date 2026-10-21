import type { StatusMeta } from './status';

/**
 * AI 治理（提示词管理、调用日志）用到的状态与数值显示（V1.0.1.6 界面规范）：每个状态给出文字与类别，
 * 页面用 StatusTag 显示。通用的状态映射见 utils/status.ts。
 */

/** 提示词版本：生效中为已完成，其余为中性 */
export function getPromptVersionMeta(active: boolean): StatusMeta {
  return active
    ? { label: '生效中', tone: 'done' }
    : { label: '未启用', tone: 'neutral' };
}

/** 提示词与内置默认是否一致：一致为中性，改过为进行中（提醒管理员这是自定义内容） */
export function getPromptDefaultMeta(sameAsDefault: boolean): StatusMeta {
  return sameAsDefault
    ? { label: '与默认一致', tone: 'neutral' }
    : { label: '已修改', tone: 'running' };
}

/** 生效版本的文字：没有启用版本时用内置默认 */
export function getPromptActiveVersionLabel(
  usingDefault: boolean,
  activeVersion?: null | number,
): string {
  if (usingDefault || activeVersion === null || activeVersion === undefined) {
    return '内置默认';
  }
  return `第 ${activeVersion} 版`;
}

/** 调用结果 */
export function getAiCallResultMeta(success: boolean): StatusMeta {
  return success
    ? { label: '成功', tone: 'done' }
    : { label: '失败', tone: 'failed' };
}

/** 调用失败的类型（后端的失败分类），界面只显示中文 */
const AI_CALL_ERROR_LABELS: Record<string, string> = {
  TIMEOUT: '超时',
  CONNECTION: '连接失败',
  RATE_LIMITED: '服务商限流',
  SERVER_ERROR: '服务端错误',
  CLIENT_ERROR: '请求被拒绝',
  INTERRUPTED: '被中断',
  QUOTA_EXCEEDED: '已达用量上限',
  CIRCUIT_OPEN: '熔断中',
  CANCELLED: '已取消',
  OTHER: '其他',
};

/** 失败类型的中文；成功或没有类型时为空，认不出的类型归为「其他」 */
export function getAiCallErrorLabel(
  errorType: null | string | undefined,
): string {
  if (!errorType) {
    return '';
  }
  return AI_CALL_ERROR_LABELS[errorType] ?? '其他';
}

/** 令牌数：服务商没有返回用量时为「—」，其余按千分位 */
export function formatAiTokens(value: null | number | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return Number(value).toLocaleString('zh-CN');
}

/** 耗时：毫秒换成秒，保留一位小数（「3.2 秒」） */
export function formatAiDuration(durationMs: null | number | undefined) {
  if (
    durationMs === null ||
    durationMs === undefined ||
    Number.isNaN(durationMs)
  ) {
    return '—';
  }
  return `${(Math.max(0, durationMs) / 1000).toFixed(1)} 秒`;
}

/** 按天汇总的合计 */
export interface AiCallTotals {
  callCount: number;
  failedCount: number;
  totalTokens: number;
}

export function sumAiCallDaily(
  rows: null | readonly Partial<AiCallTotals>[] | undefined,
): AiCallTotals {
  const totals: AiCallTotals = { callCount: 0, failedCount: 0, totalTokens: 0 };
  for (const row of rows ?? []) {
    totals.callCount += row.callCount ?? 0;
    totals.failedCount += row.failedCount ?? 0;
    totals.totalTokens += row.totalTokens ?? 0;
  }
  return totals;
}
