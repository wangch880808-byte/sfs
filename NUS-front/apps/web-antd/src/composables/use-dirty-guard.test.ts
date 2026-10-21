import type { DialogConfirmCopy } from '#/utils/confirm-copy';

import { enableAutoUnmount, mount } from '@vue/test-utils';
import { defineComponent, h, nextTick, ref } from 'vue';
import { createMemoryHistory, createRouter, RouterView } from 'vue-router';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { snapshotForm, useDirtyGuard } from './use-dirty-guard';

const confirmMock = vi.hoisted(() => vi.fn());
vi.mock('@vben/common-ui', () => ({ confirm: confirmMock }));

enableAutoUnmount(afterEach);

function setup(dirtyInitial = true, answer = false) {
  const dirty = ref(dirtyInitial);
  const ask = vi.fn(async (_copy: DialogConfirmCopy) => answer);
  let guard!: ReturnType<typeof useDirtyGuard>;
  const Editor = defineComponent({
    setup() {
      guard = useDirtyGuard(dirty, { confirm: ask });
      return () => h('div', 'editor');
    },
  });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { component: Editor, path: '/edit' },
      { component: Editor, path: '/item/:id' },
      { component: { render: () => h('div', 'other') }, path: '/other' },
    ],
  });
  const App = defineComponent({ render: () => h(RouterView) });
  return { App, ask, dirty, getGuard: () => guard, router };
}

describe('useDirtyGuard', () => {
  it('asks before leaving the route and stays when the user goes back', async () => {
    const { App, ask, router } = setup(true, false);
    await router.push('/edit');
    mount(App, { global: { plugins: [router] } });
    await router.isReady();

    await router.push('/other');
    expect(ask).toHaveBeenCalledTimes(1);
    expect(ask.mock.calls[0]?.[0]).toMatchObject({
      cancelText: '返回',
      confirmText: '放弃修改',
    });
    expect(router.currentRoute.value.path).toBe('/edit');
  });

  it('leaves after the user chooses to discard, and never asks when clean', async () => {
    const discard = setup(true, true);
    await discard.router.push('/edit');
    mount(discard.App, { global: { plugins: [discard.router] } });
    await discard.router.push('/other');
    expect(discard.router.currentRoute.value.path).toBe('/other');

    const clean = setup(false, false);
    await clean.router.push('/edit');
    mount(clean.App, { global: { plugins: [clean.router] } });
    await clean.router.push('/other');
    expect(clean.ask).not.toHaveBeenCalled();
    expect(clean.router.currentRoute.value.path).toBe('/other');
  });

  it('asks when the same page switches to another path, not for query changes', async () => {
    const { App, ask, router } = setup(true, false);
    await router.push('/item/1');
    mount(App, { global: { plugins: [router] } });
    await router.push('/item/1?tab=2');
    expect(ask).not.toHaveBeenCalled();
    expect(router.currentRoute.value.fullPath).toBe('/item/1?tab=2');
    await router.push('/item/2');
    expect(ask).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value.path).toBe('/item/1');
  });

  it('skipGuard lets a successful save navigate without asking', async () => {
    const { App, ask, getGuard, router } = setup(true, false);
    await router.push('/edit');
    mount(App, { global: { plugins: [router] } });
    await getGuard().skipGuard(() => router.push('/other'));
    expect(ask).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/other');
    expect(getGuard().isGuarding.value).toBe(true);
  });

  it('blocks tab close only while dirty and removes the listener on unmount', async () => {
    const { App, dirty, router } = setup(true, false);
    await router.push('/edit');
    const wrapper = mount(App, { global: { plugins: [router] } });

    const blocked = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(blocked);
    expect(blocked.defaultPrevented).toBe(true);

    dirty.value = false;
    const allowed = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(allowed);
    expect(allowed.defaultPrevented).toBe(false);

    dirty.value = true;
    wrapper.unmount();
    const afterUnmount = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(afterUnmount);
    expect(afterUnmount.defaultPrevented).toBe(false);
  });

  it('onBeforeClose confirms once for concurrent close attempts', async () => {
    const ask = vi.fn(
      () => new Promise<boolean>((resolve) => setTimeout(() => resolve(true))),
    );
    const guard = useDirtyGuard(() => true, { confirm: ask });
    const [first, second] = await Promise.all([
      guard.onBeforeClose(),
      guard.onBeforeClose(),
    ]);
    expect(first).toBe(true);
    expect(second).toBe(true);
    expect(ask).toHaveBeenCalledTimes(1);
  });

  it('does not ask twice after the user chose to discard, until changes are cleared', async () => {
    const dirty = ref(true);
    const ask = vi.fn(async () => true);
    let guard!: ReturnType<typeof useDirtyGuard>;
    const Host = defineComponent({
      setup() {
        guard = useDirtyGuard(dirty, { confirm: ask, routeLeave: false });
        return () => h('div');
      },
    });
    mount(Host);
    expect(await guard.confirmDiscard()).toBe(true);
    expect(await guard.onBeforeClose()).toBe(true);
    expect(ask).toHaveBeenCalledTimes(1);

    // 改动清空后再改，重新询问
    dirty.value = false;
    await nextTick();
    dirty.value = true;
    await nextTick();
    await guard.confirmDiscard();
    expect(ask).toHaveBeenCalledTimes(2);
  });

  it('bindPopup disables mask close while dirty', async () => {
    const dirty = ref(false);
    const setState = vi.fn();
    const Host = defineComponent({
      setup() {
        useDirtyGuard(dirty, {
          beforeUnload: false,
          confirm: async () => true,
          routeLeave: false,
        }).bindPopup({ setState });
        return () => h('div');
      },
    });
    mount(Host);
    expect(setState).toHaveBeenLastCalledWith({ closeOnClickModal: true });
    dirty.value = true;
    await nextTick();
    expect(setState).toHaveBeenLastCalledWith({ closeOnClickModal: false });
  });

  it('uses the vben confirm dialog by default and treats cancel as stay', async () => {
    const guard = useDirtyGuard(() => true);
    confirmMock.mockRejectedValueOnce(new Error('dialog cancelled'));
    expect(await guard.confirmDiscard()).toBe(false);
    confirmMock.mockResolvedValueOnce(undefined);
    expect(await guard.confirmDiscard()).toBe(true);
    expect(confirmMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ confirmText: '放弃修改', icon: 'warning' }),
    );
  });
});

describe('snapshotForm', () => {
  it('treats undefined, null and empty string as the same empty value', () => {
    expect(snapshotForm({ a: undefined, b: '' })).toBe(
      snapshotForm({ a: null, b: null }),
    );
    expect(snapshotForm({ a: 1 })).not.toBe(snapshotForm({ a: 2 }));
    expect(snapshotForm(undefined)).toBe('null');
  });
});
