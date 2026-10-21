import type { TaskFilterRow } from './task-filter';

import { describe, expect, it } from 'vitest';

import {
  buildTaskOwnerOptions,
  defaultTaskFilter,
  filterTaskRows,
  getCourseTaskRowActions,
  getCourseTaskStatusMeta,
  getTaskOwnerKey,
  getTaskOwnerLabel,
  isTaskPending,
  isTaskSealed,
} from './task-filter';

const NOW = new Date('2026-10-13T12:00:00').getTime();

const rows: Array<TaskFilterRow & { id: number }> = [
  { id: 1, status: 1, teacherId: 7, teacherName: '王老师' },
  { id: 2, status: 3, teacherId: 5, teacherName: '李老师' },
  { id: 3, status: 0, teacherId: 7, teacherName: '王老师' },
  { id: 4, status: 2, teacherId: 9, teacherName: '赵老师' },
  { id: 5, status: 1, teacherName: '-' },
];

describe('课程详情：任务的封存与待发布', () => {
  it('状态 3 为已封存，字符串也算', () => {
    expect(isTaskSealed({ status: 3 })).toBe(true);
    expect(isTaskSealed({ status: '3' })).toBe(true);
    expect(isTaskSealed({ status: 1 })).toBe(false);
    expect(isTaskSealed({})).toBe(false);
    expect(isTaskSealed(undefined)).toBe(false);
  });

  it('状态 0 为待发布；没有状态（旧版后端）不算', () => {
    expect(isTaskPending({ status: 0 })).toBe(true);
    expect(isTaskPending({})).toBe(false);
    expect(isTaskPending({ status: null })).toBe(false);
    expect(isTaskPending({ status: '' })).toBe(false);
  });

  it('状态标签：已封存（灰）、待发布，其余按截止时间', () => {
    expect(getCourseTaskStatusMeta({ status: 3 }, NOW)).toEqual({
      label: '已封存',
      tone: 'neutral',
    });
    expect(getCourseTaskStatusMeta({ status: 0 }, NOW).label).toBe('待发布');
    expect(
      getCourseTaskStatusMeta(
        { deadline: '2026-10-01 00:00:00', status: 1 },
        NOW,
      ).label,
    ).toBe('已截止');
    expect(
      getCourseTaskStatusMeta(
        { deadline: '2026-12-01 00:00:00', status: 1 },
        NOW,
      ).label,
    ).toBe('进行中');
    // 已封存优先于截止时间
    expect(
      getCourseTaskStatusMeta(
        { deadline: '2026-10-01 00:00:00', status: 3 },
        NOW,
      ).label,
    ).toBe('已封存');
  });
});

describe('课程详情：任务筛选', () => {
  it('默认不显示已封存的任务', () => {
    expect(defaultTaskFilter()).toEqual({
      ownerKey: undefined,
      showSealed: false,
    });
    expect(filterTaskRows(rows, defaultTaskFilter()).map((r) => r.id)).toEqual([
      1, 3, 4, 5,
    ]);
  });

  it('打开显示已封存后全部显示', () => {
    expect(filterTaskRows(rows, { showSealed: true })).toHaveLength(5);
  });

  it('按负责人筛选，与显示已封存一起生效', () => {
    expect(
      filterTaskRows(rows, { ownerKey: 'id:7', showSealed: false }).map(
        (r) => r.id,
      ),
    ).toEqual([1, 3]);
    expect(
      filterTaskRows(rows, { ownerKey: 'id:5', showSealed: false }),
    ).toHaveLength(0);
    expect(
      filterTaskRows(rows, { ownerKey: 'id:5', showSealed: true }).map(
        (r) => r.id,
      ),
    ).toEqual([2]);
  });

  it('负责人取值与文字：查不到（-）时文字为「-」、不出选项', () => {
    expect(getTaskOwnerKey(rows[0] as TaskFilterRow)).toBe('id:7');
    expect(getTaskOwnerKey({ teacherName: '张老师' })).toBe('name:张老师');
    expect(getTaskOwnerKey(rows[4] as TaskFilterRow)).toBe('');
    expect(getTaskOwnerLabel(rows[4] as TaskFilterRow)).toBe('-');
    expect(getTaskOwnerLabel({})).toBe('-');
  });

  it('负责人选项：去重、按姓名排序', () => {
    expect(buildTaskOwnerOptions(rows)).toEqual([
      { label: '李老师', value: 'id:5' },
      { label: '王老师', value: 'id:7' },
      { label: '赵老师', value: 'id:9' },
    ]);
  });
});

describe('课程详情：任务行的操作', () => {
  it('未封存：编辑、封存、复制、删除', () => {
    expect(getCourseTaskRowActions({ status: 1 }, true)).toEqual({
      copy: true,
      edit: true,
      remove: true,
      restore: false,
      seal: true,
    });
  });

  it('已封存：恢复、复制、删除，不能编辑', () => {
    expect(getCourseTaskRowActions({ status: 3 }, true)).toEqual({
      copy: true,
      edit: false,
      remove: true,
      restore: true,
      seal: false,
    });
  });

  it('不能管理（不是布置者）：只能复制，含已封存的任务', () => {
    expect(getCourseTaskRowActions({ status: 1 }, false)).toEqual({
      copy: true,
      edit: false,
      remove: false,
      restore: false,
      seal: false,
    });
    expect(getCourseTaskRowActions({ status: 3 }, false)).toEqual({
      copy: true,
      edit: false,
      remove: false,
      restore: false,
      seal: false,
    });
  });
});
