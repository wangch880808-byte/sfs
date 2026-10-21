import type { DraftContent } from './use-homework-draft';

import { enableAutoUnmount, mount } from '@vue/test-utils';
import { defineComponent, h, nextTick, ref } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  isSameDraftContent,
  parseHomeworkDraft,
  useHomeworkDraft,
} from './use-homework-draft';

const routeLeaveHandlers: Array<() => unknown> = [];
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (handler: () => unknown) => {
    routeLeaveHandlers.push(handler);
  },
}));

enableAutoUnmount(afterEach);

function storageMock(initial: Record<string, string> = {}): Storage {
  const values = new Map(Object.entries(initial));
  return {
    get length() {
      return values.size;
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => {
      values.delete(key);
    },
    setItem: (key, value) => {
      values.set(key, value);
    },
  };
}

const KEY = 'homework_draft_7_9';
const EMPTY: DraftContent = { attachments: [], content: '' };

function createDraft(
  storage: Storage,
  state: { baseline?: DraftContent; current: DraftContent; enabled?: boolean },
  now = () => 1000,
) {
  return useHomeworkDraft({
    baseline: () => state.baseline ?? EMPTY,
    enabled: () => state.enabled ?? true,
    key: () => KEY,
    now,
    read: () => state.current,
    storage,
  });
}

describe('草稿格式', () => {
  it('读新格式，兼容旧版纯文字草稿', () => {
    expect(
      parseHomeworkDraft(
        JSON.stringify({
          attachments: [{ name: '图片1.png', size: 10, url: 'u1' }, { x: 1 }],
          content: '答案',
          savedAt: 5,
        }),
      ),
    ).toEqual({
      attachments: [{ name: '图片1.png', size: 10, url: 'u1' }],
      content: '答案',
      savedAt: 5,
    });
    expect(parseHomeworkDraft('旧草稿')).toEqual({
      attachments: [],
      content: '旧草稿',
      savedAt: 0,
    });
    expect(parseHomeworkDraft('123')?.content).toBe('123');
    expect(parseHomeworkDraft(null)).toBeNull();
  });

  it('附件按地址比较', () => {
    const a = { attachments: [{ name: 'a', url: 'u1' }], content: 'x' };
    expect(
      isSameDraftContent(a, {
        attachments: [{ name: '改名', url: 'u1' }],
        content: 'x',
      }),
    ).toBe(true);
    expect(isSameDraftContent(a, { attachments: [], content: 'x' })).toBe(
      false,
    );
  });
});

describe('草稿保存与恢复', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    routeLeaveHandlers.length = 0;
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('改动后约 2 秒保存文字和附件', () => {
    const storage = storageMock();
    const state = {
      current: {
        attachments: [{ name: '图片1.png', size: 3, url: 'u1' }],
        content: '实验过程',
      },
    };
    const draft = createDraft(storage, state);
    draft.scheduleSave();
    vi.advanceTimersByTime(1999);
    expect(storage.getItem(KEY)).toBeNull();
    vi.advanceTimersByTime(1);
    expect(parseHomeworkDraft(storage.getItem(KEY))).toEqual({
      ...state.current,
      savedAt: 1000,
    });
    expect(draft.lastSavedAt.value).toBe(1000);
  });

  it('内容清空或与已提交的相同时删除草稿', () => {
    const storage = storageMock({ [KEY]: '旧内容' });
    const state = { current: { attachments: [], content: '  ' } };
    const draft = createDraft(storage, state);
    draft.flush();
    expect(storage.getItem(KEY)).toBeNull();

    storage.setItem(KEY, '旧内容');
    const same = { attachments: [], content: '已提交的答案' };
    createDraft(storage, { baseline: same, current: same }).flush();
    expect(storage.getItem(KEY)).toBeNull();
  });

  it('不能提交时不保存也不恢复', () => {
    const storage = storageMock({ [KEY]: '草稿' });
    const draft = createDraft(storage, {
      current: { attachments: [], content: '新内容' },
      enabled: false,
    });
    draft.flush();
    expect(storage.getItem(KEY)).toBe('草稿');
    expect(draft.restore()).toBeNull();
  });

  it('只恢复比上次提交晚、且与已提交内容不同的草稿', () => {
    const storage = storageMock({
      [KEY]: JSON.stringify({
        attachments: [],
        content: '改过的',
        savedAt: 500,
      }),
    });
    const state = {
      baseline: { attachments: [], content: '原来的' },
      current: EMPTY,
    };
    expect(createDraft(storage, state).restore(600)).toBeNull();
    const draft = createDraft(storage, state);
    expect(draft.restore(400)?.content).toBe('改过的');
    expect(draft.restoredDraft.value?.content).toBe('改过的');
    // 旧版草稿没有保存时间，照常恢复
    storage.setItem(KEY, '旧版草稿');
    expect(createDraft(storage, state).restore(600)?.content).toBe('旧版草稿');
    // 与已提交的相同时不恢复
    storage.setItem(KEY, '原来的');
    expect(createDraft(storage, state).restore()).toBeNull();
  });

  it('提交成功后清除草稿并取消待保存', () => {
    const storage = storageMock();
    const draft = createDraft(storage, {
      current: { attachments: [], content: '答案' },
    });
    draft.scheduleSave();
    draft.clear();
    vi.advanceTimersByTime(3000);
    expect(storage.getItem(KEY)).toBeNull();
  });

  it('在页面里：输入后自动保存，切到后台和离开页面时立即保存', async () => {
    const storage = storageMock();
    const content = ref('');
    const Host = defineComponent({
      setup() {
        useHomeworkDraft({
          baseline: () => EMPTY,
          enabled: () => true,
          key: () => KEY,
          read: () => ({ attachments: [], content: content.value }),
          storage,
        });
        return () => h('div');
      },
    });
    const wrapper = mount(Host);

    content.value = '第一段';
    await nextTick();
    vi.advanceTimersByTime(2000);
    expect(parseHomeworkDraft(storage.getItem(KEY))?.content).toBe('第一段');

    content.value = '第二段';
    await nextTick();
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    });
    document.dispatchEvent(new Event('visibilitychange'));
    expect(parseHomeworkDraft(storage.getItem(KEY))?.content).toBe('第二段');
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });

    content.value = '第三段';
    await nextTick();
    expect(routeLeaveHandlers).toHaveLength(1);
    routeLeaveHandlers[0]?.();
    expect(parseHomeworkDraft(storage.getItem(KEY))?.content).toBe('第三段');

    content.value = '第四段';
    await nextTick();
    wrapper.unmount();
    expect(parseHomeworkDraft(storage.getItem(KEY))?.content).toBe('第四段');
  });
});
