import { describe, expect, it } from 'vitest';

import { buildRemoveConfirm } from './remove-confirm';

describe('删除任务的确认文案', () => {
  it('有提交时写明删除的提交与成绩份数、统计不再计入、不能恢复', () => {
    const copy = buildRemoveConfirm({
      homeworkCount: 3,
      studentCount: 40,
      submittedCount: 12,
    });
    expect(copy.danger).toBe(true);
    expect(copy.title).toBe('删除选中的 3 个实训任务？');
    expect(copy.content).toContain(
      '将删除 12 份提交和成绩，所有统计不再计入，且不能恢复',
    );
    expect(copy.content).toContain('封存');
  });

  it('没有提交时简短确认', () => {
    const copy = buildRemoveConfirm({
      homeworkCount: 1,
      studentCount: 30,
      submittedCount: 0,
    });
    expect(copy).toEqual({
      content: '还没有学生提交。删除后不能恢复。',
      danger: true,
      title: '删除这个实训任务？',
    });
  });

  it('只删一个任务且知道名称时，标题写名称', () => {
    expect(
      buildRemoveConfirm(
        { homeworkCount: 1, studentCount: 0, submittedCount: 2 },
        '组件化重构',
      ).title,
    ).toBe('删除实训任务「组件化重构」？');
    // 多个任务时名称不用
    expect(
      buildRemoveConfirm(
        { homeworkCount: 2, studentCount: 0, submittedCount: 0 },
        '组件化重构',
      ).title,
    ).toBe('删除选中的 2 个实训任务？');
  });

  it('影响范围缺字段或为空时按一个任务、没有提交处理', () => {
    expect(buildRemoveConfirm(undefined)).toEqual({
      content: '还没有学生提交。删除后不能恢复。',
      danger: true,
      title: '删除这个实训任务？',
    });
    expect(buildRemoveConfirm({ submittedCount: Number.NaN }).content).toBe(
      '还没有学生提交。删除后不能恢复。',
    );
  });
});
