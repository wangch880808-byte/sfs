import type { Ref, ShallowRef } from 'vue';

import { computed, ref, shallowRef } from 'vue';

/**
 * 看板上一块数据的读取状态（V1.0.1.6 界面规范：部分失败只让失败的那块降级）。
 * - 第一次读取时 loading 为 true，读到之后再刷新不清空已有数据，页面不闪；
 * - 出错时只记下 error，提示交给请求层（会弹出原因），这一块显示“没读到”和重试；
 * - 同时发起多次读取时只保留最后一次的结果；
 * - enabled 返回 false 时不发请求（没有权限的接口不调，免得弹出无权限提示），这一块按不可用处理。
 */
export interface AsyncData<T> {
  data: ShallowRef<T | undefined>;
  /** 这一块能不能读（有没有权限） */
  enabled: Readonly<Ref<boolean>>;
  error: Ref<boolean>;
  /** 正在读取（包括刷新） */
  loading: Ref<boolean>;
  /** 至少成功读到过一次 */
  loaded: Ref<boolean>;
  load: () => Promise<void>;
}

export function useAsyncData<T>(
  fetcher: () => Promise<T>,
  options: { enabled?: () => boolean } = {},
): AsyncData<T> {
  const data = shallowRef<T>();
  const error = ref(false);
  const loading = ref(false);
  const loaded = ref(false);
  const enabled = computed(() => options.enabled?.() ?? true);
  let requestId = 0;

  async function load() {
    if (!enabled.value) {
      return;
    }
    const current = ++requestId;
    loading.value = true;
    try {
      const result = await fetcher();
      if (current !== requestId) return;
      data.value = result;
      error.value = false;
      loaded.value = true;
    } catch {
      if (current !== requestId) return;
      error.value = true;
    } finally {
      if (current === requestId) loading.value = false;
    }
  }

  return { data, enabled, error, loading, loaded, load };
}

/**
 * 面板的显示状态：用到的几块数据都读到才显示内容。任一块出错且没有旧数据时显示“没读到”，
 * 否则只要还有一块没读到就显示加载（页面挂载后马上开始读，不会先闪一下空状态）。
 * 没有权限、不会去读的数据不要传进来。
 */
export interface PanelState {
  error: boolean;
  loading: boolean;
}

export function combinePanelState(
  sources: Array<Pick<AsyncData<unknown>, 'error' | 'loaded'>>,
): PanelState {
  const error = sources.some((item) => item.error.value && !item.loaded.value);
  const loading = !error && sources.some((item) => !item.loaded.value);
  return { error, loading };
}
