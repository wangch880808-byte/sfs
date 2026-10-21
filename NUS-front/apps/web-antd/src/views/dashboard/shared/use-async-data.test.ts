import { describe, expect, it, vi } from 'vitest';

import { combinePanelState, useAsyncData } from './use-async-data';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, reject, resolve };
}

describe('看板数据的读取状态', () => {
  it('读到后记下数据；刷新时保留旧数据', async () => {
    let value = 1;
    const source = useAsyncData(async () => value);
    expect(source.loaded.value).toBe(false);
    await source.load();
    expect(source.data.value).toBe(1);
    expect(source.loaded.value).toBe(true);
    expect(source.loading.value).toBe(false);

    value = 2;
    const refreshing = source.load();
    expect(source.loading.value).toBe(true);
    expect(source.data.value).toBe(1);
    await refreshing;
    expect(source.data.value).toBe(2);
  });

  it('出错时只记下 error，不抛出；再次读到后清掉', async () => {
    let fail = true;
    const source = useAsyncData(async () => {
      if (fail) throw new Error('网络错误');
      return 'ok';
    });
    await expect(source.load()).resolves.toBeUndefined();
    expect(source.error.value).toBe(true);
    expect(source.loaded.value).toBe(false);
    fail = false;
    await source.load();
    expect(source.error.value).toBe(false);
    expect(source.data.value).toBe('ok');
  });

  it('同时发起多次读取时只保留最后一次的结果', async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    const fetcher = vi
      .fn<() => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const source = useAsyncData(fetcher);
    const a = source.load();
    const b = source.load();
    second.resolve('新');
    await b;
    first.resolve('旧');
    await a;
    expect(source.data.value).toBe('新');
    expect(source.loading.value).toBe(false);
  });

  it('没有权限时不发请求', async () => {
    const fetcher = vi.fn(async () => 1);
    const source = useAsyncData(fetcher, { enabled: () => false });
    await source.load();
    expect(fetcher).not.toHaveBeenCalled();
    expect(source.enabled.value).toBe(false);
  });
});

describe('面板的显示状态', () => {
  it('还有一块没读到时显示加载，没读到且出错时显示没读到，都读到时显示内容', async () => {
    const ok = useAsyncData(async () => 1);
    const bad = useAsyncData(async () => {
      throw new Error('失败');
    });
    expect(combinePanelState([ok, bad])).toEqual({
      error: false,
      loading: true,
    });
    await ok.load();
    await bad.load();
    expect(combinePanelState([ok, bad])).toEqual({
      error: true,
      loading: false,
    });
    expect(combinePanelState([ok])).toEqual({ error: false, loading: false });
  });

  it('读到过的数据刷新失败时继续显示旧内容', async () => {
    let fail = false;
    const source = useAsyncData(async () => {
      if (fail) throw new Error('失败');
      return 1;
    });
    await source.load();
    fail = true;
    await source.load();
    expect(combinePanelState([source])).toEqual({
      error: false,
      loading: false,
    });
  });
});
