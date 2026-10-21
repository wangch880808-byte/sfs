/**
 * 批改页的接口错误：请求带 silentError 时全局不提示，由页面就地显示。
 * 业务错误的形状是 { data: { code, msg } }，网络错误没有 code。
 */

/** 状态已变化（条件更新没有命中）与已被他人更新（乐观校验）：都提示刷新这一份 */
export const GRADING_CONFLICT_CODES = new Set([1_050_005_100, 1_050_005_101]);

/** 撤销：找不到、已过期、操作后又有变化 */
export const GRADING_UNDO_FAILED_CODES = new Set([
  1_050_005_102, 1_050_005_103, 1_050_005_104,
]);

interface ErrorLike {
  data?: { code?: unknown; msg?: unknown };
  message?: unknown;
  msg?: unknown;
  response?: { data?: { code?: unknown; msg?: unknown } };
}

export function getRequestErrorCode(error: unknown): number | undefined {
  if (!error || typeof error !== 'object') return undefined;
  const e = error as ErrorLike;
  const code = Number(e.data?.code ?? e.response?.data?.code);
  return Number.isFinite(code) ? code : undefined;
}

export function getRequestErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (!error || typeof error !== 'object') return fallback;
  const e = error as ErrorLike;
  const msg = e.data?.msg ?? e.response?.data?.msg ?? e.msg;
  if (typeof msg === 'string' && msg.trim()) return msg;
  return fallback;
}

export function isGradingConflict(error: unknown): boolean {
  const code = getRequestErrorCode(error);
  return code !== undefined && GRADING_CONFLICT_CODES.has(code);
}
