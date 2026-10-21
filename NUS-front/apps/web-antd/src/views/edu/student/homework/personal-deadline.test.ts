import { describe, expect, it } from 'vitest';

import { getDeadlineBadge } from './personal-deadline';

describe('学生任务列表的截止时间标注', () => {
  it('已打回且有重做期限时标「重做」', () => {
    expect(
      getDeadlineBadge({
        personalDeadline: '2026-10-12 18:00:00',
        redoDeadline: '2026-10-15 18:00:00',
        status: 3,
      }),
    ).toBe('重做');
  });

  it('还没提交且有个别延期时标「延期」', () => {
    expect(
      getDeadlineBadge({ personalDeadline: '2026-10-12 18:00:00', status: 0 }),
    ).toBe('延期');
  });

  it('已经提交或没有延期时不标注', () => {
    expect(
      getDeadlineBadge({ personalDeadline: '2026-10-12 18:00:00', status: 1 }),
    ).toBe('');
    expect(getDeadlineBadge({ personalDeadline: null, status: 0 })).toBe('');
    expect(getDeadlineBadge({ status: 0 })).toBe('');
  });
});
