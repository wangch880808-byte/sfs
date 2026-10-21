/**
 * 后台轮询（比如顶栏的未读通知数）：
 * - 页面在后台（document.hidden）时跳过，不发请求；
 * - 请求失败不提示，按 2 倍退避，最长到 maxIntervalMs；成功后恢复正常间隔；
 * - 同一时间只有一个请求在途。
 * 请求本身应带 silentError，失败原因不弹全局提示。
 */
export interface BackgroundPollerOptions {
  /** 一次轮询；抛错表示失败 */
  task: () => Promise<unknown>;
  /** 正常间隔（毫秒） */
  intervalMs: number;
  /** 失败退避的最长间隔（毫秒），默认 5 分钟 */
  maxIntervalMs?: number;
  /** 页面是否在后台；默认读 document.hidden */
  isHidden?: () => boolean;
}

export interface BackgroundPoller {
  /** 立即执行一次并开始轮询 */
  start: () => void;
  stop: () => void;
  /**
   * 页面回到前台、窗口获得焦点、用户操作后立即刷新一次。
   * 处于失败退避时只有 force 才会立即请求，避免切换窗口时反复打到不可用的后端。
   */
  refresh: (options?: { force?: boolean }) => Promise<void>;
  /** 连续失败次数 */
  getFailures: () => number;
  /** 下一次轮询的间隔（毫秒） */
  getNextDelay: () => number;
}

export const DEFAULT_POLL_MAX_INTERVAL_MS = 5 * 60 * 1000;

function defaultIsHidden() {
  return typeof document !== 'undefined' && document.hidden;
}

export function createBackgroundPoller(
  options: BackgroundPollerOptions,
): BackgroundPoller {
  const intervalMs = options.intervalMs;
  const maxIntervalMs = Math.max(
    intervalMs,
    options.maxIntervalMs ?? DEFAULT_POLL_MAX_INTERVAL_MS,
  );
  const isHidden = options.isHidden ?? defaultIsHidden;
  let failures = 0;
  let stopped = true;
  let inFlight: null | Promise<void> = null;
  let timer: ReturnType<typeof setTimeout> | undefined;

  function getNextDelay() {
    if (failures === 0) {
      return intervalMs;
    }
    return Math.min(intervalMs * 2 ** failures, maxIntervalMs);
  }

  function clearTimer() {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  }

  function schedule() {
    clearTimer();
    if (stopped) {
      return;
    }
    timer = setTimeout(tick, getNextDelay());
  }

  function run(): Promise<void> {
    if (inFlight) {
      return inFlight;
    }
    inFlight = (async () => {
      try {
        await options.task();
        failures = 0;
      } catch {
        failures += 1;
      } finally {
        inFlight = null;
        schedule();
      }
    })();
    return inFlight;
  }

  function tick() {
    timer = undefined;
    if (stopped) {
      return;
    }
    if (isHidden()) {
      // 在后台不请求，按当前间隔继续等
      schedule();
      return;
    }
    void run();
  }

  return {
    start() {
      if (!stopped) {
        return;
      }
      stopped = false;
      if (isHidden()) {
        schedule();
        return;
      }
      void run();
    },
    stop() {
      stopped = true;
      clearTimer();
    },
    async refresh(refreshOptions) {
      if (stopped || isHidden()) {
        return;
      }
      if (failures > 0 && !refreshOptions?.force) {
        return;
      }
      await run();
    },
    getFailures: () => failures,
    getNextDelay,
  };
}
