import { describe, expect, it } from 'vitest';

import { formatListTime, formatMessageTime, nameInitial } from './format';

const now = new Date(2026, 9, 6, 21, 0, 0);

describe('formatMessageTime', () => {
  it('shows the time to the minute', () => {
    expect(formatMessageTime('2026-10-06T19:46:12')).toBe('2026-10-06 19:46');
    expect(formatMessageTime('2026-10-06 19:46:12')).toBe('2026-10-06 19:46');
  });

  it('keeps empty values empty', () => {
    expect(formatMessageTime()).toBe('');
    expect(formatMessageTime(null)).toBe('');
  });
});

describe('formatListTime', () => {
  it('shows only the clock time for today', () => {
    expect(formatListTime('2026-10-06T19:46:12', now)).toBe('19:46');
  });

  it('shows month and day for earlier days this year', () => {
    expect(formatListTime('2026-09-30 08:05:00', now)).toBe('09-30');
  });

  it('shows the full date for earlier years', () => {
    expect(formatListTime('2025-12-31 23:59:00', now)).toBe('2025-12-31');
  });

  it('falls back to the raw text when it is not a full time', () => {
    expect(formatListTime('', now)).toBe('');
    expect(formatListTime('2026-10', now)).toBe('2026-10');
  });
});

describe('nameInitial', () => {
  it('uses the first character of the name', () => {
    expect(nameInitial('李晓雨')).toBe('李');
    expect(nameInitial('  Alice ')).toBe('A');
  });

  it('falls back to a question mark', () => {
    expect(nameInitial('')).toBe('?');
    expect(nameInitial(undefined)).toBe('?');
  });
});
