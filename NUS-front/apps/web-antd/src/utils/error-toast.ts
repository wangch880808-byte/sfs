/**
 * 全局错误提示去重：后端重启或断网时，同一时刻会有多个请求以同样的原因失败。
 * 同一条文字在窗口期内只提示一次；窗口期过后再出现会重新提示。
 */

/** 去重窗口：3 秒内的同一条提示只显示一次 */
export const GLOBAL_ERROR_DEDUPE_MS = 3000;

/** antd message 的 key：同一条提示复用同一个浮层，不叠加 */
export function getGlobalErrorKey(text: string) {
  return `global-error:${text.trim()}`;
}

/**
 * 返回判断函数：这条提示现在要不要显示。
 * @param windowMs 去重窗口（毫秒）
 * @param now 取当前时间（测试时注入）
 */
export function createErrorToastDeduper(
  windowMs = GLOBAL_ERROR_DEDUPE_MS,
  now: () => number = () => Date.now(),
) {
  const lastShownAt = new Map<string, number>();
  return function shouldShow(text: string): boolean {
    const key = text.trim();
    if (!key) {
      return false;
    }
    const current = now();
    for (const [shownText, shownAt] of lastShownAt) {
      if (current - shownAt >= windowMs) {
        lastShownAt.delete(shownText);
      }
    }
    if (lastShownAt.has(key)) {
      return false;
    }
    lastShownAt.set(key, current);
    return true;
  };
}
