import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  buildTaskMeta,
  buildTaskRouteQuery,
  getTaskLevelLabel,
  getTaskTypeLabel,
  readTaskRouteQuery,
} from './task-display';

const dict = vi.hoisted(() => ({
  labels: {} as Record<string, string>,
}));

vi.mock('@vben/hooks', () => ({
  getDictLabel: (type: string, value: unknown) =>
    dict.labels[`${type}:${value}`] ?? '',
}));

describe('任务类型与等级', () => {
  beforeEach(() => {
    dict.labels = {};
  });

  it('字典有值时以字典为准', () => {
    dict.labels = {
      'edu_homework_level:0': '专业',
      'edu_homework_type:2': '考核任务',
    };
    expect(getTaskTypeLabel(2)).toBe('考核任务');
    expect(getTaskLevelLabel(0)).toBe('专业');
  });

  it('字典没加载时按原来的写法兜底', () => {
    expect(getTaskTypeLabel(1)).toBe('平时任务');
    expect(getTaskTypeLabel(2)).toBe('考核任务');
    expect(getTaskLevelLabel(1)).toBe('初级');
    expect(getTaskLevelLabel('2')).toBe('中级');
    expect(getTaskLevelLabel(3)).toBe('高级');
    expect(getTaskLevelLabel(undefined)).toBe('未设置等级');
  });

  it('页头说明按课程、等级、类型排列，缺课程时不留空位', () => {
    expect(
      buildTaskMeta({
        courseName: 'Web 前端开发实训',
        homeworkLevel: 2,
        homeworkType: 1,
      }),
    ).toBe('Web 前端开发实训 · 中级 · 平时任务');
    expect(buildTaskMeta({ homeworkLevel: 1, homeworkType: 2 })).toBe(
      '初级 · 考核任务',
    );
  });
});

describe('任务页的课程与等级参数', () => {
  it('带上课程和等级，读回时容错', () => {
    expect(buildTaskRouteQuery({ courseId: 3, homeworkLevel: 2 })).toEqual({
      courseId: '3',
      level: '2',
    });
    expect(
      buildTaskRouteQuery({ courseId: undefined, homeworkLevel: 0 }),
    ).toEqual({ level: '0' });
    expect(readTaskRouteQuery({ courseId: '3', level: '2' })).toEqual({
      courseId: 3,
      level: 2,
    });
    expect(readTaskRouteQuery({ courseId: 'x', level: ['1'] })).toEqual({
      courseId: undefined,
      level: 1,
    });
    expect(readTaskRouteQuery({})).toEqual({
      courseId: undefined,
      level: undefined,
    });
  });
});
