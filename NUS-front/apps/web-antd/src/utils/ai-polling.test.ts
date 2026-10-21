import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { startAiPolling } from './ai-polling';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('aI polling', () => {
  it('runs serial requests with increasing intervals and stops on completion', async () => {
    const request = vi
      .fn()
      .mockResolvedValueOnce(0)
      .mockResolvedValueOnce(0)
      .mockResolvedValueOnce(1);
    const onResult = vi.fn();
    const onTimeout = vi.fn();
    const handle = startAiPolling({
      request,
      isPending: (status) => status === 0,
      onResult,
      onError: vi.fn(),
      onTimeout,
      initialIntervalMs: 20,
      maxIntervalMs: 40,
      maxDurationMs: 200,
    });

    await vi.advanceTimersByTimeAsync(19);
    expect(request).toHaveBeenCalledTimes(0);
    await vi.advanceTimersByTimeAsync(1);
    expect(request).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(29);
    expect(request).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(request).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(40);
    expect(request).toHaveBeenCalledTimes(3);
    expect(onResult).toHaveBeenCalledTimes(3);
    expect(handle.isActive()).toBe(false);
    await vi.advanceTimersByTimeAsync(200);
    expect(onTimeout).not.toHaveBeenCalled();
  });

  it('times out while a request is pending and ignores its late result', async () => {
    let resolveRequest!: (value: number) => void;
    const request = vi.fn(
      () =>
        new Promise<number>((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const onResult = vi.fn();
    const onTimeout = vi.fn();
    const handle = startAiPolling({
      request,
      isPending: () => true,
      onResult,
      onError: vi.fn(),
      onTimeout,
      initialIntervalMs: 20,
      maxDurationMs: 50,
    });
    await vi.advanceTimersByTimeAsync(20);
    await vi.advanceTimersByTimeAsync(30);
    expect(onTimeout).toHaveBeenCalledTimes(1);
    expect(handle.isActive()).toBe(false);
    resolveRequest(0);
    await Promise.resolve();
    expect(onResult).not.toHaveBeenCalled();
  });

  it('ignores a stopped request and does not schedule another one', async () => {
    let resolveRequest!: (value: number) => void;
    const request = vi.fn(
      () =>
        new Promise<number>((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const onResult = vi.fn();
    const handle = startAiPolling({
      request,
      isPending: () => true,
      onResult,
      onError: vi.fn(),
      onTimeout: vi.fn(),
      initialIntervalMs: 20,
      maxDurationMs: 100,
    });
    await vi.advanceTimersByTimeAsync(20);
    handle.stop();
    resolveRequest(0);
    await vi.advanceTimersByTimeAsync(100);
    expect(request).toHaveBeenCalledTimes(1);
    expect(onResult).not.toHaveBeenCalled();
  });
});
