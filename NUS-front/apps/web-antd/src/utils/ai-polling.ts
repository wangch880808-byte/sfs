interface AiPollingOptions<T> {
  request: () => Promise<T>;
  isPending: (result: T) => boolean;
  onResult: (result: T) => void;
  onError: (error: unknown) => void;
  onTimeout: () => void;
  initialIntervalMs?: number;
  maxIntervalMs?: number;
  maxDurationMs?: number;
}

export interface AiPollingHandle {
  stop: () => void;
  isActive: () => boolean;
}

/** 串行轮询；停止后忽略未完成请求，且总时长包含请求耗时。 */
export function startAiPolling<T>(
  options: AiPollingOptions<T>,
): AiPollingHandle {
  const initialInterval = options.initialIntervalMs ?? 2000;
  const maxInterval = options.maxIntervalMs ?? 10_000;
  const deadline = Date.now() + (options.maxDurationMs ?? 600_000);
  let interval = initialInterval;
  let active = true;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeoutTimer = setTimeout(() => {
    stop();
    options.onTimeout();
  }, options.maxDurationMs ?? 600_000);

  const stop = () => {
    active = false;
    if (timer) clearTimeout(timer);
    clearTimeout(timeoutTimer);
    timer = undefined;
  };

  const schedule = () => {
    if (!active) return;
    const remaining = deadline - Date.now();
    if (remaining <= 0) {
      stop();
      options.onTimeout();
      return;
    }
    timer = setTimeout(run, Math.min(interval, remaining));
    interval = Math.min(Math.ceil(interval * 1.5), maxInterval);
  };

  const run = async () => {
    timer = undefined;
    if (!active) return;
    try {
      const result = await options.request();
      if (!active) return;
      options.onResult(result);
      if (!active) return;
      if (!options.isPending(result)) {
        stop();
        return;
      }
      schedule();
    } catch (error) {
      if (!active) return;
      stop();
      options.onError(error);
    }
  };

  schedule();
  return { stop, isActive: () => active };
}
