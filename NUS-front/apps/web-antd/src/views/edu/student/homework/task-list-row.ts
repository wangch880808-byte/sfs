import type { StatusMeta } from '#/utils/status';

import { canSubmitHomework } from '#/utils/homework-deadline';
import { getStudentTodoStatusMeta } from '#/utils/status-dashboard';
import { isAwaitingSubmissionStatus } from '#/utils/status-student-homework';

/**
 * 任务中心列表一行的状态与操作（V1.0.1.8 C1）。
 * 任务所在等级还没解锁时（后端 locked），状态写「未解锁」，操作改为「查看解锁条件」，打开课程详情看还差什么；
 * 往期任务只读，已经交过的任务照常看成绩反馈，都不按未解锁处理。
 */
export interface TaskListRow {
  canSubmit?: boolean;
  courseId?: null | number;
  deadline?: null | number | string;
  effectiveDeadline?: null | number | string;
  historyOnly?: boolean;
  homeworkId: number;
  locked?: boolean;
  lockReason?: string;
  personalDeadline?: null | number | string;
  redoDeadline?: null | number | string;
  status?: number;
}

export type TaskListActionKind = 'submit' | 'unlock' | 'view';

export interface TaskListAction {
  kind: TaskListActionKind;
  label: string;
  /** 「查看解锁条件」的悬浮提示：没解锁的原因 */
  tooltip?: string;
}

/** 按未解锁处理：后端标了 locked，不是往期任务，且还等着学生提交（未提交或已打回） */
export function isTaskLocked(row: TaskListRow): boolean {
  return (
    row.locked === true &&
    !row.historyOnly &&
    isAwaitingSubmissionStatus(row.status)
  );
}

export function getTaskListStatusMeta(row: TaskListRow): StatusMeta {
  return getStudentTodoStatusMeta({
    locked: isTaskLocked(row),
    status: row.status,
  });
}

export function getTaskListAction(
  row: TaskListRow,
  now = Date.now(),
): TaskListAction {
  if (isTaskLocked(row)) {
    const reason = row.lockReason?.trim();
    return {
      kind: 'unlock',
      label: '查看解锁条件',
      ...(reason ? { tooltip: reason } : {}),
    };
  }
  if (!row.historyOnly && canSubmitHomework(row, now)) {
    return { kind: 'submit', label: '去提交' };
  }
  return { kind: 'view', label: '查看' };
}

/** 「查看解锁条件」打开的课程详情；缺课程时为 undefined */
export function getTaskUnlockPath(row: TaskListRow): string | undefined {
  return row.courseId ? `/student/course/detail/${row.courseId}` : undefined;
}
