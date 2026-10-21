import { describe, expect, it } from 'vitest';

import {
  clearLegacyHomeworkDrafts,
  clearUserHomeworkDrafts,
  getHomeworkDraftKey,
} from './homework-draft';

function storageMock(initial: Record<string, string>): Storage {
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

describe('homework draft storage', () => {
  it('isolates users and homework tasks', () => {
    expect(getHomeworkDraftKey(7, 9)).toBe('homework_draft_7_9');
    expect(getHomeworkDraftKey(8, 9)).not.toBe(getHomeworkDraftKey(7, 9));
    expect(getHomeworkDraftKey(7, 10)).not.toBe(getHomeworkDraftKey(7, 9));
  });

  it('clears legacy shared keys without reading their contents', () => {
    const storage = storageMock({
      homework_draft_9: 'old',
      homework_draft_7_9: 'current',
    });
    clearLegacyHomeworkDrafts(storage);
    expect(storage.getItem('homework_draft_9')).toBeNull();
    expect(storage.getItem('homework_draft_7_9')).toBe('current');
  });

  it('clears current user drafts on logout and keeps other users drafts', () => {
    const storage = storageMock({
      homework_draft_9: 'old',
      homework_draft_7_9: 'mine',
      homework_draft_70_9: 'other',
    });
    clearUserHomeworkDrafts(storage, 7);
    expect(storage.getItem('homework_draft_9')).toBeNull();
    expect(storage.getItem('homework_draft_7_9')).toBeNull();
    expect(storage.getItem('homework_draft_70_9')).toBe('other');
  });
});
