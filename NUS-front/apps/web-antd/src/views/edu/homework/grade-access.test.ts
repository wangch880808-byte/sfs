import { describe, expect, it } from 'vitest';

import {
  HOMEWORK_VIEW_ONLY_NOTICE,
  HOMEWORK_VIEW_ONLY_REASON,
  isHomeworkViewOnly,
} from './grade-access';

describe('任务批改权限', () => {
  it('后端明确说不能批改时只读', () => {
    expect(isHomeworkViewOnly({ canGrade: false })).toBe(true);
  });

  it('能批改、旧版后端没有 canGrade、还没读到任务时按原来的样子可批改', () => {
    expect(isHomeworkViewOnly({ canGrade: true })).toBe(false);
    expect(isHomeworkViewOnly({})).toBe(false);
    expect(isHomeworkViewOnly(undefined)).toBe(false);
  });

  it('只读说明写明只能查看、批改由布置者完成', () => {
    expect(HOMEWORK_VIEW_ONLY_NOTICE).toContain('布置者');
    expect(HOMEWORK_VIEW_ONLY_NOTICE).toContain('只能查看');
    expect(HOMEWORK_VIEW_ONLY_REASON).toContain('布置者');
  });
});
