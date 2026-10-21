import type { StudentCourseApi } from '#/api/edu/student/course';

import { describe, expect, it } from 'vitest';

import {
  buildLevelRuleHint,
  describeLevel,
  getThresholdPercent,
} from './level-progress';

function level(
  patch: Partial<StudentCourseApi.UnlockLevel>,
): StudentCourseApi.UnlockLevel {
  return {
    level: 2,
    levelName: '中级',
    totalTasks: 0,
    qualifiedTasks: 0,
    qualificationRate: 0,
    unlocked: true,
    ...patch,
  };
}

describe('等级闯关显示', () => {
  it('按计入的任务数显示达标进度', () => {
    const view = describeLevel(
      level({
        totalTasks: 5,
        passedCount: 3,
        requiredCount: 4,
        ratio: 0.75,
        threshold: 0.8,
      }),
    );
    expect(view.countText).toBe('3/4 个达标 · 75%');
    expect(view.percent).toBe(75);
  });

  it('未解锁时第二行写后端给出的原因', () => {
    const view = describeLevel(
      level({
        unlocked: false,
        reasonText: '初级还差 1 个任务达标（需达标 80%）',
      }),
    );
    expect(view.unlocked).toBe(false);
    expect(view.detailText).toBe('初级还差 1 个任务达标（需达标 80%）');
  });

  it('旧接口没有原因时给出默认说明', () => {
    expect(describeLevel(level({ unlocked: false })).detailText).toBe(
      '上一等级达标后解锁',
    );
  });

  it('有任务但都没到截止也没提交时不显示 0%', () => {
    const view = describeLevel(
      level({ totalTasks: 2, requiredCount: 0, ratio: null }),
    );
    expect(view.countText).toBe('任务还没有截止或提交');
    expect(view.percent).toBe(0);
  });

  it('没有任务时写暂无任务', () => {
    expect(describeLevel(level({})).countText).toBe('暂无任务');
  });

  it('已解锁时优先写待批改数，其次写解锁日期', () => {
    expect(
      describeLevel(level({ pendingReviewCount: 2, requiredCount: 3 }))
        .detailText,
    ).toBe('2 个任务在等教师批改');
    expect(
      describeLevel(level({ unlockedAt: '2026-10-05 08:00:00' })).detailText,
    ).toBe('2026-10-05 解锁');
  });

  it('旧接口回退到 qualifiedTasks、totalTasks', () => {
    const view = describeLevel(
      level({ totalTasks: 5, qualifiedTasks: 4, qualificationRate: 80 }),
    );
    expect(view.countText).toBe('4/5 个达标 · 80%');
  });

  it('规则说明带上阈值且不超过 60 字', () => {
    const levels = [level({ threshold: 0.8 })];
    expect(getThresholdPercent(levels)).toBe(80);
    const hint = buildLevelRuleHint(levels);
    expect(hint).toContain('达到 80%');
    expect(hint.length).toBeLessThanOrEqual(60);
    expect(buildLevelRuleHint([level({})])).toContain('达到要求');
  });
});
