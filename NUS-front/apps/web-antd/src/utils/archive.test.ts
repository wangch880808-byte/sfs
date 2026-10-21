import { describe, expect, it } from 'vitest';

import { ARCHIVE_COPY, archiveConfirm, isArchived } from './archive';

describe('封存状态和文案', () => {
  it('按模块状态码识别已封存', () => {
    expect(isArchived(1)).toBe(true);
    expect(isArchived(0)).toBe(false);
    expect(isArchived(3, 3)).toBe(true);
    expect(isArchived(2, 3)).toBe(false);
  });

  it('封存确认写明对象、后果与恢复办法，不用“确认……吗”', () => {
    const text = archiveConfirm('课程');
    expect(text).toBe(`封存「课程」？${ARCHIVE_COPY.archiveHint}。`);
    expect(text).not.toMatch(/确认.*吗/);
  });
});
