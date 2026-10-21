import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createBackgroundPoller } from './background-poller';

describe('background poller', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('runs immediately on start and then every interval', async () => {
    const task = vi.fn().mockResolvedValue(1);
    const poller = createBackgroundPoller({
      intervalMs: 1000,
      isHidden: () => false,
      task,
    });
    poller.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(task).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1000);
    expect(task).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1000);
    expect(task).toHaveBeenCalledTimes(3);
    poller.stop();
    await vi.advanceTimersByTimeAsync(5000);
    expect(task).toHaveBeenCalledTimes(3);
  });

  it('skips requests while the page is hidden', async () => {
    let hidden = true;
    const task = vi.fn().mockResolvedValue(1);
    const poller = createBackgroundPoller({
      intervalMs: 1000,
      isHidden: () => hidden,
      task,
    });
    poller.start();
    await vi.advanceTimersByTimeAsync(3000);
    expect(task).not.toHaveBeenCalled();
    await poller.refresh({ force: true });
    expect(task).not.toHaveBeenCalled();
    hidden = false;
    await vi.advanceTimersByTimeAsync(1000);
    expect(task).toHaveBeenCalledTimes(1);
    poller.stop();
  });

  it('backs off after failures and recovers after a success', async () => {
    const task = vi.fn().mockRejectedValue(new Error('Network Error'));
    const poller = createBackgroundPoller({
      intervalMs: 1000,
      isHidden: () => false,
      maxIntervalMs: 5000,
      task,
    });
    poller.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(poller.getFailures()).toBe(1);
    expect(poller.getNextDelay()).toBe(2000);

    await vi.advanceTimersByTimeAsync(2000);
    expect(task).toHaveBeenCalledTimes(2);
    expect(poller.getNextDelay()).toBe(4000);

    await vi.advanceTimersByTimeAsync(4000);
    expect(task).toHaveBeenCalledTimes(3);
    // 封顶
    expect(poller.getNextDelay()).toBe(5000);

    task.mockResolvedValue(0);
    await vi.advanceTimersByTimeAsync(5000);
    expect(task).toHaveBeenCalledTimes(4);
    expect(poller.getFailures()).toBe(0);
    expect(poller.getNextDelay()).toBe(1000);
    poller.stop();
  });

  it('does not let focus refreshes bypass the backoff unless forced', async () => {
    const task = vi.fn().mockRejectedValue(new Error('down'));
    const poller = createBackgroundPoller({
      intervalMs: 1000,
      isHidden: () => false,
      task,
    });
    poller.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(task).toHaveBeenCalledTimes(1);
    await poller.refresh();
    expect(task).toHaveBeenCalledTimes(1);
    await poller.refresh({ force: true });
    expect(task).toHaveBeenCalledTimes(2);
    poller.stop();
  });

  it('keeps a single request in flight', async () => {
    let resolve!: () => void;
    const task = vi.fn(
      () =>
        new Promise<void>((r) => {
          resolve = r;
        }),
    );
    const poller = createBackgroundPoller({
      intervalMs: 1000,
      isHidden: () => false,
      task,
    });
    poller.start();
    void poller.refresh();
    void poller.refresh();
    expect(task).toHaveBeenCalledTimes(1);
    resolve();
    await vi.advanceTimersByTimeAsync(0);
    poller.stop();
  });
});
