import type { Ref } from 'vue';

import {
  getCurrentInstance,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

/**
 * 学生提交页的本机草稿（V1.0.1.7 S8）：
 * - 文字改动后约 2 秒保存；离开页面、切到后台、关闭标签页时立即保存；
 * - 草稿包括已上传完成的附件（地址、文件名、大小），恢复后不用重新上传；
 * - 与已提交的内容相同（或内容清空）时删除草稿；提交成功后由页面调用 clear()；
 * - 只恢复比上次提交更晚的草稿。
 * 草稿 key 按用户和任务隔离（utils/homework-draft.ts），登出时清除。
 */

export interface DraftAttachment {
  name: string;
  /** 字节数；从服务器读回的旧附件没有大小 */
  size?: number;
  url: string;
}

export interface DraftContent {
  attachments: DraftAttachment[];
  content: string;
}

export interface HomeworkDraft extends DraftContent {
  /** 保存时间（毫秒） */
  savedAt: number;
}

export const DRAFT_SAVE_DEBOUNCE_MS = 2000;

function normalizeAttachments(value: unknown): DraftAttachment[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter(
      (item) =>
        item &&
        typeof item === 'object' &&
        typeof item.url === 'string' &&
        item.url.trim(),
    )
    .map((item) => ({
      name: typeof item.name === 'string' && item.name ? item.name : '附件',
      size: Number(item.size) > 0 ? Number(item.size) : undefined,
      url: item.url,
    }));
}

/** 读草稿；兼容旧版只存文字的草稿 */
export function parseHomeworkDraft(raw: null | string): HomeworkDraft | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw);
    if (
      parsed &&
      typeof parsed === 'object' &&
      !Array.isArray(parsed) &&
      typeof parsed.content === 'string'
    ) {
      return {
        attachments: normalizeAttachments(parsed.attachments),
        content: parsed.content,
        savedAt: Number(parsed.savedAt) || 0,
      };
    }
  } catch {
    // 旧版草稿是纯文字
  }
  return { attachments: [], content: raw, savedAt: 0 };
}

export function isDraftEmpty(draft: DraftContent) {
  return !draft.content.trim() && draft.attachments.length === 0;
}

/** 两份内容是否相同：文字比较全文，附件按地址顺序比较 */
export function isSameDraftContent(a: DraftContent, b: DraftContent) {
  return (
    a.content === b.content &&
    a.attachments.length === b.attachments.length &&
    a.attachments.every((item, index) => item.url === b.attachments[index]?.url)
  );
}

export interface UseHomeworkDraftOptions {
  /** 草稿 key；空字符串表示不保存（还不知道用户或任务） */
  key: () => string;
  /** 当前填写的内容 */
  read: () => DraftContent;
  /** 已提交的内容（没提交过为空）；与之相同时不留草稿 */
  baseline: () => DraftContent;
  /** 当前能不能保存（能提交且还没提交成功） */
  enabled: () => boolean;
  storage?: Storage;
  debounceMs?: number;
  now?: () => number;
}

export function useHomeworkDraft(options: UseHomeworkDraftOptions) {
  const debounceMs = options.debounceMs ?? DRAFT_SAVE_DEBOUNCE_MS;
  const now = options.now ?? (() => Date.now());
  const lastSavedAt: Ref<null | number> = ref(null);
  /** 本次打开页面时恢复的草稿 */
  const restoredDraft: Ref<HomeworkDraft | null> = ref(null);
  let timer: ReturnType<typeof setTimeout> | undefined;

  function getStorage(): null | Storage {
    try {
      return options.storage ?? globalThis.localStorage ?? null;
    } catch {
      return null;
    }
  }

  function cancelScheduled() {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  }

  function remove() {
    const key = options.key();
    if (!key) {
      return;
    }
    try {
      getStorage()?.removeItem(key);
    } catch {
      // 浏览器禁用存储时忽略
    }
  }

  /** 立即保存；内容为空或与已提交的相同时删除草稿 */
  function flush() {
    cancelScheduled();
    const key = options.key();
    if (!key || !options.enabled()) {
      return;
    }
    const current = options.read();
    if (
      isDraftEmpty(current) ||
      isSameDraftContent(current, options.baseline())
    ) {
      remove();
      lastSavedAt.value = null;
      return;
    }
    const draft: HomeworkDraft = { ...current, savedAt: now() };
    try {
      getStorage()?.setItem(key, JSON.stringify(draft));
      lastSavedAt.value = draft.savedAt;
    } catch {
      // 存储已满或被禁用：不影响填写和提交
    }
  }

  /** 改动后约 2 秒保存 */
  function scheduleSave() {
    if (!options.enabled()) {
      return;
    }
    cancelScheduled();
    timer = setTimeout(flush, debounceMs);
  }

  /**
   * 读出可以恢复的草稿：比上次提交晚、与已提交内容不同、不为空。
   * @param submittedAt 上次提交时间（毫秒），没提交过不传
   */
  function restore(submittedAt?: number): HomeworkDraft | null {
    const key = options.key();
    if (!key || !options.enabled()) {
      return null;
    }
    let raw: null | string = null;
    try {
      raw = getStorage()?.getItem(key) ?? null;
    } catch {
      raw = null;
    }
    const draft = parseHomeworkDraft(raw);
    if (
      !draft ||
      isDraftEmpty(draft) ||
      isSameDraftContent(draft, options.baseline()) ||
      (submittedAt !== undefined &&
        draft.savedAt > 0 &&
        draft.savedAt <= submittedAt)
    ) {
      return null;
    }
    restoredDraft.value = draft;
    lastSavedAt.value = draft.savedAt || null;
    return draft;
  }

  /** 提交成功或放弃草稿：删除并停止待保存 */
  function clear() {
    cancelScheduled();
    remove();
    lastSavedAt.value = null;
    restoredDraft.value = null;
  }

  function handleVisibilityChange() {
    if (document.visibilityState === 'hidden') {
      flush();
    }
  }

  if (getCurrentInstance()) {
    watch(
      () => {
        const current = options.read();
        return [
          current.content,
          current.attachments.map((item) => item.url).join('\n'),
        ];
      },
      () => scheduleSave(),
    );
    onBeforeRouteLeave(() => {
      flush();
    });
    onMounted(() => {
      document.addEventListener('visibilitychange', handleVisibilityChange);
      window.addEventListener('pagehide', flush);
    });
    onBeforeUnmount(() => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('pagehide', flush);
      flush();
    });
  }

  return {
    clear,
    flush,
    lastSavedAt,
    restore,
    restoredDraft,
    scheduleSave,
  };
}
