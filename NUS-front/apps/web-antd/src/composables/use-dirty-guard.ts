import type { MaybeRefOrGetter } from 'vue';

import type { DialogConfirmCopy } from '#/utils/confirm-copy';

import {
  computed,
  getCurrentInstance,
  onBeforeUnmount,
  onMounted,
  toValue,
  watch,
} from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';

import { confirm } from '@vben/common-ui';

import { discardChangesConfirm } from '#/utils/confirm-copy';

/**
 * 有没保存的修改时，离开前先确认（V1.0.1.7 S8）：
 * - 路由离开（onBeforeRouteLeave，以及同一页面换路径参数的 onBeforeRouteUpdate）弹确认框，选“放弃修改”才离开；
 * - 关闭或刷新浏览器标签页（beforeunload）由浏览器提示；
 * - vben 弹窗、抽屉：把 onBeforeClose 传给 useVbenModal/useVbenDrawer，
 *   再用 bindPopup(api) 让有改动时点遮罩不关闭。
 * 保存成功后关闭或跳转，用 skipGuard 包一下，这一次不再询问。
 */
export interface DirtyGuardOptions {
  /** 确认框文案，默认 discardChangesConfirm() */
  copy?: DialogConfirmCopy;
  /** 是否拦截路由离开，默认 true */
  routeLeave?: boolean;
  /** 是否拦截关闭或刷新标签页，默认 true */
  beforeUnload?: boolean;
  /** 弹确认框：选放弃返回 true，选返回或关掉返回 false（测试时注入） */
  confirm?: (copy: DialogConfirmCopy) => Promise<boolean>;
}

/** vben 弹窗、抽屉的 api 里用到的部分 */
interface PopupApiLike {
  setState: (state: { closeOnClickModal?: boolean }) => unknown;
}

async function defaultConfirm(copy: DialogConfirmCopy) {
  try {
    await confirm({ ...copy, icon: 'warning' });
    return true;
  } catch {
    return false;
  }
}

export function useDirtyGuard(
  isDirty: MaybeRefOrGetter<boolean>,
  options: DirtyGuardOptions = {},
) {
  const ask = options.confirm ?? defaultConfirm;
  let bypassDepth = 0;
  let pending: null | Promise<boolean> = null;
  /**
   * 已经选了放弃：在改动清空（弹窗关闭、页面重置）之前不再询问。
   * 例如路由离开时确认放弃后，keep-alive 失活又会关闭页面里的弹窗，不能再问一遍。
   */
  let discarded = false;

  function shouldGuard() {
    return bypassDepth === 0 && !discarded && !!toValue(isDirty);
  }

  /** 没有改动时直接放行；有改动时确认，同一时间只弹一个确认框 */
  function confirmDiscard(): Promise<boolean> {
    if (!shouldGuard()) {
      return Promise.resolve(true);
    }
    if (!pending) {
      pending = ask(options.copy ?? discardChangesConfirm())
        .then((confirmed) => {
          if (confirmed) {
            discarded = true;
          }
          return confirmed;
        })
        .finally(() => {
          pending = null;
        });
    }
    return pending;
  }

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (!shouldGuard()) {
      return;
    }
    event.preventDefault();
    // 旧版浏览器要求设置 returnValue 才会提示
    event.returnValue = '';
  }

  if (getCurrentInstance()) {
    watch(
      () => !!toValue(isDirty),
      (dirty) => {
        if (!dirty) {
          discarded = false;
        }
      },
    );
    if (options.routeLeave !== false) {
      onBeforeRouteLeave(() => confirmDiscard());
      // 同一个页面换了路径参数（比如从一个任务的提交页直接到另一个任务）也算离开；只改查询参数不算
      onBeforeRouteUpdate((to, from) =>
        to.path === from.path ? true : confirmDiscard(),
      );
    }
    if (options.beforeUnload !== false) {
      onMounted(() => {
        window.addEventListener('beforeunload', handleBeforeUnload);
      });
      onBeforeUnmount(() => {
        window.removeEventListener('beforeunload', handleBeforeUnload);
      });
    }
  }

  /** 保存成功后的关闭、跳转：执行期间不询问 */
  async function skipGuard<T>(action: () => Promise<T> | T): Promise<T> {
    bypassDepth += 1;
    try {
      return await action();
    } finally {
      bypassDepth -= 1;
    }
  }

  /** 有改动时点遮罩不关闭弹窗、抽屉（关闭按钮、取消按钮、Esc 仍走 onBeforeClose 确认） */
  function bindPopup(api: PopupApiLike) {
    if (!getCurrentInstance()) {
      api.setState({ closeOnClickModal: !toValue(isDirty) });
      return;
    }
    watch(
      () => !!toValue(isDirty),
      (dirty) => {
        api.setState({ closeOnClickModal: !dirty });
      },
      { immediate: true },
    );
  }

  return {
    bindPopup,
    confirmDiscard,
    /** 当前是否会拦截 */
    isGuarding: computed(() => shouldGuard()),
    /** 传给 useVbenModal / useVbenDrawer 的 onBeforeClose */
    onBeforeClose: confirmDiscard,
    skipGuard,
  };
}

/**
 * 表单快照比较：打开或保存时记下快照，之后与当前值比较判断有没有改动。
 * 值里的 dayjs 等对象按 JSON 序列化比较；undefined、null、'' 视为同一个“空”。
 */
export function snapshotForm(value: unknown): string {
  return JSON.stringify(value ?? null, (_key, item) =>
    item === undefined || item === '' ? null : item,
  );
}
