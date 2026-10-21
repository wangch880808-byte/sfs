import { describe, expect, it } from 'vitest';

import { resolvePassScoreChange } from './pass-score-change';

describe('修改达标分数线的提示', () => {
  it('已发布任务改了分数线时说明影响', () => {
    const change = resolvePassScoreChange(
      { id: 1, passScore: 60, status: 1 },
      70,
    );
    expect(change?.from).toBe(60);
    expect(change?.to).toBe(70);
    expect(change?.message).toContain('从 60 分改为 70 分');
    expect(change?.message).toContain('不会收回');
  });

  it('新建、待发布或没改分数线时不提示', () => {
    expect(resolvePassScoreChange(undefined, 70)).toBeNull();
    expect(resolvePassScoreChange({ passScore: 60, status: 1 }, 70)).toBeNull();
    expect(
      resolvePassScoreChange({ id: 1, passScore: 60, status: 0 }, 70),
    ).toBeNull();
    expect(
      resolvePassScoreChange({ id: 1, passScore: 60, status: 1 }, '60'),
    ).toBeNull();
  });
});
