import type { StudentHomeworkApi } from '#/api/edu/student/homework';

import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  buildFeedbackItems,
  buildStudentSummary,
  collectLockedLevels,
  collectTodoCourseIds,
  countDueSoonTodos,
  getCourseProgress,
  pickTodoTasks,
  sumUnread,
} from './student';

const at = (value: string) => dayjs(value).valueOf();
const NOW = at('2026-10-06 20:00:00');

const task = (
  homeworkId: number,
  status: number,
  deadline: string,
  extra: Partial<StudentHomeworkApi.HomeworkItem> = {},
): StudentHomeworkApi.HomeworkItem => ({
  courseId: 1,
  courseName: 'Web 前端开发实训',
  deadline,
  homeworkId,
  homeworkLevel: 1,
  homeworkName: `任务${homeworkId}`,
  homeworkType: 1,
  passScore: 60,
  score: 100,
  status,
  ...extra,
});

const list = [
  task(1, 2, '2026-10-13 19:46:04'),
  task(2, 1, '2026-10-16 19:46:04'),
  task(3, 0, '2026-10-20 19:46:04', { homeworkLevel: 2 }),
  task(4, 0, '2026-10-08 10:00:00'),
  task(5, 3, '2026-10-01 10:00:00', { redoDeadline: '2026-10-07 12:00:00' }),
  task(6, 0, '2026-10-01 10:00:00'),
  task(7, 0, '2026-10-30 10:00:00', { courseId: 2 }),
];

describe('学生首页：待完成', () => {
  it('需要读解锁状态的是还有可提交任务的课程', () => {
    expect(collectTodoCourseIds(list, NOW)).toEqual([1, 2]);
  });

  it('每门课没解锁的等级', () => {
    const locked = collectLockedLevels([
      [
        1,
        {
          levels: [
            {
              level: 1,
              levelName: '初级',
              qualificationRate: 50,
              qualifiedTasks: 1,
              totalTasks: 2,
              unlocked: true,
            },
            {
              level: 2,
              levelName: '中级',
              qualificationRate: 0,
              qualifiedTasks: 0,
              totalTasks: 2,
              unlocked: false,
            },
          ],
        },
      ],
      [2, undefined],
    ]);
    expect(locked).toEqual(new Map([[1, new Set([2])]]));
  });

  it('未提交与已打回、还没过有效截止时间的才算；未解锁的排后面；过期的单独计数', () => {
    const todo = pickTodoTasks(list, new Map([[1, new Set([2])]]), NOW);
    expect(todo.items.map((item) => [item.homeworkId, item.locked])).toEqual([
      [5, false],
      [4, false],
      [7, false],
      [3, true],
    ]);
    // 已打回的以重做期限为准
    expect(todo.items[0]).toMatchObject({
      deadline: at('2026-10-07 12:00:00'),
      status: 3,
    });
    expect(todo.overdueCount).toBe(1);
  });

  it('3 天内截止的只算能做的', () => {
    const todo = pickTodoTasks(list, new Map(), NOW);
    expect(countDueSoonTodos(todo, NOW)).toBe(2);
    const lockedTodo = pickTodoTasks(
      [task(4, 0, '2026-10-08 10:00:00', { homeworkLevel: 2 })],
      new Map([[1, new Set([2])]]),
      NOW,
    );
    expect(countDueSoonTodos(lockedTodo, NOW)).toBe(0);
  });
});

describe('学生首页：最新反馈', () => {
  it('名称在已批改任务里唯一对上时给出任务编号', () => {
    const items = buildFeedbackItems(
      [
        {
          correctTime: '2026-10-06 19:46:09',
          homeworkName: '任务1',
          score: 88,
        },
        {
          correctTime: '2026-10-05 10:00:00',
          homeworkName: '同名任务',
          score: 70,
        },
        { correctTime: '', homeworkName: '对不上的任务', score: 60 },
      ],
      [
        ...list,
        task(8, 2, '2026-10-01 10:00:00', { homeworkName: '同名任务' }),
        task(9, 2, '2026-10-01 10:00:00', {
          courseId: 2,
          homeworkName: '同名任务',
        }),
      ],
    );
    expect(items.map((item) => item.homeworkId)).toEqual([
      1,
      undefined,
      undefined,
    ]);
    expect(items[0]).toMatchObject({
      correctTime: at('2026-10-06 19:46:09'),
      homeworkName: '任务1',
      score: 88,
    });
    expect(items[2]?.correctTime).toBeUndefined();
  });
});

describe('学生首页：留言与课程进度', () => {
  it('未读留言合计，忽略异常值', () => {
    expect(
      sumUnread([
        { unreadCount: 2 },
        { unreadCount: 0 },
        { unreadCount: -1 },
        { unreadCount: Number.NaN },
      ] as never),
    ).toBe(2);
  });

  it('课程进度取已完成 / 总数，不超过总数', () => {
    expect(
      getCourseProgress({ completedHomework: 1, totalHomework: 4 }),
    ).toEqual({ completed: 1, percent: 25, total: 4 });
    expect(
      getCourseProgress({ completedHomework: 5, totalHomework: 4 }),
    ).toEqual({ completed: 4, percent: 100, total: 4 });
    expect(getCourseProgress({})).toEqual({
      completed: 0,
      percent: 0,
      total: 0,
    });
  });
});

describe('学生首页：页头说明', () => {
  it('能做的、即将截止、等待解锁与未读留言', () => {
    const todo = pickTodoTasks(list, new Map([[1, new Set([2])]]), NOW);
    expect(buildStudentSummary({ todo, unread: 1 }, NOW)).toBe(
      '3 项任务待完成，2 项 3 天内截止，另有 1 项等待解锁，1 条留言未读。',
    );
  });

  it('只剩未解锁的任务时说明怎么解锁；什么都没有时说没有', () => {
    const lockedOnly = pickTodoTasks(
      [task(3, 0, '2026-10-20 19:46:04', { homeworkLevel: 2 })],
      new Map([[1, new Set([2])]]),
      NOW,
    );
    expect(buildStudentSummary({ todo: lockedOnly }, NOW)).toBe(
      '1 项任务等待解锁，先完成上一等级的任务。',
    );
    expect(
      buildStudentSummary({ todo: { items: [], overdueCount: 0 } }, NOW),
    ).toBe('目前没有待完成的任务。');
    expect(buildStudentSummary({}, NOW)).toBe('目前没有待完成的任务。');
  });
});
