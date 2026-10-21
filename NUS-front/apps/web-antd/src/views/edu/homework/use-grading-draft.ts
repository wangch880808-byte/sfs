import type { Ref } from 'vue';

import { computed, ref } from 'vue';

/**
 * 批改草稿：评分与评语按提交记录存在 sessionStorage（同一标签页内切换学生、刷新都不丢）。
 * 与已保存的成绩一致时视为没有草稿；保存成功后清掉。存储不可用（隐私模式等）时退化为只在内存里。
 */
export interface GradingDraft {
  comment: string;
  score: null | number;
}

/** 已保存的成绩，用来判断草稿是否改过 */
export interface SavedGrade {
  comment?: null | string;
  score?: null | number;
}

export type DraftStorage = Pick<
  Storage,
  'getItem' | 'key' | 'length' | 'removeItem' | 'setItem'
>;

const KEY_PREFIX = 'nus:grading-draft:';

export function draftStorageKey(homeworkId: number, recordId: number) {
  return `${KEY_PREFIX}${homeworkId}:${recordId}`;
}

export function isDraftDirty(draft: GradingDraft, saved: SavedGrade) {
  return (
    (draft.score ?? null) !== (saved.score ?? null) ||
    (draft.comment ?? '') !== (saved.comment ?? '')
  );
}

export function readDraft(
  storage: DraftStorage | undefined,
  homeworkId: number,
  recordId: number,
): GradingDraft | undefined {
  try {
    const raw = storage?.getItem(draftStorageKey(homeworkId, recordId));
    if (!raw) return undefined;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return undefined;
    return {
      comment: typeof parsed.comment === 'string' ? parsed.comment : '',
      score: typeof parsed.score === 'number' ? parsed.score : null,
    };
  } catch {
    return undefined;
  }
}

/** 某个任务下有草稿的提交记录 */
export function listDraftIds(
  storage: DraftStorage | undefined,
  homeworkId: number,
): number[] {
  if (!storage) return [];
  const prefix = `${KEY_PREFIX}${homeworkId}:`;
  const ids: number[] = [];
  try {
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (!key?.startsWith(prefix)) continue;
      const id = Number(key.slice(prefix.length));
      if (Number.isInteger(id) && id > 0) ids.push(id);
    }
  } catch {
    return [];
  }
  return ids;
}

function defaultStorage(): DraftStorage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage;
  } catch {
    return undefined;
  }
}

export function useGradingDraft(
  homeworkId: Ref<number>,
  storage: DraftStorage | undefined = defaultStorage(),
) {
  /** 有未保存草稿的提交记录，列表据此加标记 */
  const draftIds = ref<Set<number>>(new Set());
  /** 存储不可用时的内存兜底 */
  const memory = new Map<string, GradingDraft>();

  /**
   * 重新统计当前任务的草稿；传入 validIds 时顺手删掉已不在批改队列里的草稿（记录已删除等），
   * 免得离开页面时一直被拦。
   */
  function refresh(validIds?: Set<number>) {
    const prefix = `${KEY_PREFIX}${homeworkId.value}:`;
    const ids = new Set(listDraftIds(storage, homeworkId.value));
    for (const key of memory.keys()) {
      if (key.startsWith(prefix)) ids.add(Number(key.slice(prefix.length)));
    }
    if (validIds) {
      for (const id of ids) {
        if (validIds.has(id)) continue;
        removeStored(draftStorageKey(homeworkId.value, id));
        ids.delete(id);
      }
    }
    draftIds.value = ids;
  }

  function removeStored(key: string) {
    try {
      storage?.removeItem(key);
    } catch {
      // 存储不可用时只清内存
    }
    memory.delete(key);
  }

  function load(recordId: number): GradingDraft | undefined {
    return (
      readDraft(storage, homeworkId.value, recordId) ??
      memory.get(draftStorageKey(homeworkId.value, recordId))
    );
  }

  function markDirty(recordId: number, dirty: boolean) {
    if (draftIds.value.has(recordId) === dirty) return;
    const next = new Set(draftIds.value);
    if (dirty) next.add(recordId);
    else next.delete(recordId);
    draftIds.value = next;
  }

  /** 表单变化时调用：和已保存的成绩一样就删掉草稿 */
  function save(recordId: number, draft: GradingDraft, saved: SavedGrade) {
    if (!isDraftDirty(draft, saved)) {
      clear(recordId);
      return;
    }
    const key = draftStorageKey(homeworkId.value, recordId);
    try {
      if (!storage) throw new Error('no storage');
      storage.setItem(key, JSON.stringify(draft));
      memory.delete(key);
    } catch {
      memory.set(key, { ...draft });
    }
    markDirty(recordId, true);
  }

  function clear(recordId: number) {
    removeStored(draftStorageKey(homeworkId.value, recordId));
    markDirty(recordId, false);
  }

  const hasDrafts = computed(() => draftIds.value.size > 0);

  return { clear, draftIds, hasDrafts, load, refresh, save };
}
