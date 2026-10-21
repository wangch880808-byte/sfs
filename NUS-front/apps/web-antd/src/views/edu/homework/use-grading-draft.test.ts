import type { DraftStorage } from './use-grading-draft';

import { ref } from 'vue';

import { describe, expect, it } from 'vitest';

import {
  draftStorageKey,
  isDraftDirty,
  listDraftIds,
  readDraft,
  useGradingDraft,
} from './use-grading-draft';

function memoryStorage(): DraftStorage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    key: (index) => [...data.keys()][index] ?? null,
    get length() {
      return data.size;
    },
    removeItem: (key) => void data.delete(key),
    setItem: (key, value) => void data.set(key, value),
  };
}

describe('批改草稿', () => {
  it('与已保存的成绩一致时不算草稿', () => {
    expect(isDraftDirty({ comment: '', score: null }, {})).toBe(false);
    expect(
      isDraftDirty({ comment: '好', score: 80 }, { comment: '好', score: 80 }),
    ).toBe(false);
    expect(
      isDraftDirty({ comment: '好', score: 81 }, { comment: '好', score: 80 }),
    ).toBe(true);
    expect(
      isDraftDirty({ comment: '好 ', score: 80 }, { comment: '好', score: 80 }),
    ).toBe(true);
  });

  it('按任务与提交记录分开保存，切换学生不丢，保存后清掉', () => {
    const storage = memoryStorage();
    const drafts = useGradingDraft(ref(7), storage);
    drafts.save(
      1,
      { comment: '结构清楚', score: 86 },
      { comment: '', score: null },
    );
    drafts.save(2, { comment: '', score: null }, { comment: '', score: null });

    expect(readDraft(storage, 7, 1)).toEqual({
      comment: '结构清楚',
      score: 86,
    });
    expect(drafts.load(2)).toBeUndefined();
    expect([...drafts.draftIds.value]).toEqual([1]);
    expect(drafts.hasDrafts.value).toBe(true);
    expect(listDraftIds(storage, 8)).toEqual([]);

    // 改回已保存的成绩，草稿自动删掉
    drafts.save(1, { comment: '', score: null }, { comment: '', score: null });
    expect(storage.data.has(draftStorageKey(7, 1))).toBe(false);
    expect(drafts.hasDrafts.value).toBe(false);
  });

  it('重新统计时删掉已不在队列里的草稿', () => {
    const storage = memoryStorage();
    storage.setItem(
      draftStorageKey(7, 1),
      JSON.stringify({ comment: 'a', score: 1 }),
    );
    storage.setItem(
      draftStorageKey(7, 5),
      JSON.stringify({ comment: 'b', score: 2 }),
    );
    storage.setItem(
      draftStorageKey(9, 5),
      JSON.stringify({ comment: 'c', score: 3 }),
    );
    const drafts = useGradingDraft(ref(7), storage);
    drafts.refresh();
    expect([...drafts.draftIds.value].toSorted()).toEqual([1, 5]);
    drafts.refresh(new Set([1]));
    expect([...drafts.draftIds.value]).toEqual([1]);
    expect(storage.data.has(draftStorageKey(7, 5))).toBe(false);
    expect(storage.data.has(draftStorageKey(9, 5))).toBe(true);
  });

  it('存储不可用时退化为内存，内容照样保留', () => {
    const broken: DraftStorage = {
      getItem: () => {
        throw new Error('denied');
      },
      key: () => null,
      length: 0,
      removeItem: () => {
        throw new Error('denied');
      },
      setItem: () => {
        throw new Error('quota');
      },
    };
    const drafts = useGradingDraft(ref(7), broken);
    drafts.save(3, { comment: '先记下', score: 70 }, {});
    expect(drafts.load(3)).toEqual({ comment: '先记下', score: 70 });
    expect(drafts.draftIds.value.has(3)).toBe(true);
    drafts.clear(3);
    expect(drafts.load(3)).toBeUndefined();
  });

  it('坏数据按没有草稿处理', () => {
    const storage = memoryStorage();
    storage.setItem(draftStorageKey(7, 1), '{bad');
    expect(readDraft(storage, 7, 1)).toBeUndefined();
  });
});
