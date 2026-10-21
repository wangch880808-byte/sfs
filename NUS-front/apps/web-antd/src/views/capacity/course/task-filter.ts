import type { StatusMeta } from '#/utils/status';

import { getTaskProgressMeta } from '#/utils/status';

/**
 * 课程详情「实训任务」页签（V1.0.1.10）：后端返回的任务含已封存（3）与旧版待发布（0）。
 * 默认不显示已封存的，打开「显示已封存」后显示；可以按负责人（布置者）筛选。
 */

/** 任务状态：0 待发布（旧版草稿）、1 已发布、2 已截止、3 已封存 */
export const COURSE_TASK_STATUS = {
  PENDING: 0,
  PUBLISHED: 1,
  CLOSED: 2,
  SEALED: 3,
} as const;

/** 筛选只用到任务的这几个字段 */
export interface TaskFilterRow {
  correctedCount?: number;
  deadline?: string;
  status?: null | number | string;
  submittedCount?: number;
  teacherId?: null | number | string;
  teacherName?: null | string;
}

export interface TaskFilterState {
  /** 显示已封存的任务 */
  showSealed: boolean;
  /** 负责人：见 getTaskOwnerKey；不选（undefined）时不按负责人筛选 */
  ownerKey?: string;
}

export function defaultTaskFilter(): TaskFilterState {
  return { showSealed: false, ownerKey: undefined };
}

/** 任务已封存（字符串 '3' 也算） */
export function isTaskSealed(task?: Pick<TaskFilterRow, 'status'>): boolean {
  return (
    task?.status !== undefined &&
    task?.status !== null &&
    Number(task.status) === COURSE_TASK_STATUS.SEALED
  );
}

/** 任务是旧版的待发布（草稿） */
export function isTaskPending(task?: Pick<TaskFilterRow, 'status'>): boolean {
  return (
    task?.status !== undefined &&
    task?.status !== null &&
    task.status !== '' &&
    Number(task.status) === COURSE_TASK_STATUS.PENDING
  );
}

function realTeacherName(row: TaskFilterRow): string {
  const name = row.teacherName?.trim() ?? '';
  return name && name !== '-' ? name : '';
}

/** 负责人的筛选取值：有编号时按编号（`id:编号`），只有姓名时按姓名（`name:姓名`），都没有时为空串 */
export function getTaskOwnerKey(row: TaskFilterRow): string {
  const id = Number(row.teacherId ?? 0);
  if (Number.isFinite(id) && id > 0) {
    return `id:${id}`;
  }
  const name = realTeacherName(row);
  return name ? `name:${name}` : '';
}

/** 「负责人」列显示的文字 */
export function getTaskOwnerLabel(row: TaskFilterRow): string {
  return realTeacherName(row) || '-';
}

/** 负责人下拉的选项：从任务行去重（按编号），按姓名排序；没有负责人的行不出选项 */
export function buildTaskOwnerOptions(
  rows: TaskFilterRow[],
): Array<{ label: string; value: string }> {
  const map = new Map<string, string>();
  rows.forEach((row) => {
    const key = getTaskOwnerKey(row);
    if (key && !map.has(key)) {
      map.set(key, getTaskOwnerLabel(row));
    }
  });
  return [...map.entries()]
    .map(([value, label]) => ({ label, value }))
    .toSorted((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
}

/** 是否显示已封存的，再按负责人筛选 */
export function filterTaskRows<T extends TaskFilterRow>(
  rows: T[],
  filter: TaskFilterState,
): T[] {
  const ownerKey = filter.ownerKey || '';
  return rows.filter((row) => {
    if (!filter.showSealed && isTaskSealed(row)) {
      return false;
    }
    return ownerKey ? getTaskOwnerKey(row) === ownerKey : true;
  });
}

/**
 * 任务的状态标签：已封存（灰）、待发布；其余按截止时间与批改进度判断（进行中、已截止、已完成）。
 */
export function getCourseTaskStatusMeta(
  task: TaskFilterRow,
  now: number = Date.now(),
): StatusMeta {
  if (isTaskSealed(task)) {
    return { label: '已封存', tone: 'neutral' };
  }
  if (isTaskPending(task)) {
    return { label: '待发布', tone: 'waiting' };
  }
  return getTaskProgressMeta(task, now);
}

export interface CourseTaskRowActions {
  /** 编辑：未封存且能管理 */
  edit: boolean;
  /** 封存：未封存且能管理 */
  seal: boolean;
  /** 恢复：已封存且能管理 */
  restore: boolean;
  /** 复制：任何状态都能复制；往期封存的任务常被复制到新学期 */
  copy: boolean;
  /** 删除：能管理（任何状态都能删） */
  remove: boolean;
}

/**
 * 任务行的修改类操作。canManage 为后端判断的“能否管理”（布置者或管理员）；
 * 功能权限码仍由操作列的 auth 判断。复制是新建一个自己的任务，不要求能管理来源任务。
 */
export function getCourseTaskRowActions(
  task: Pick<TaskFilterRow, 'status'>,
  canManage: boolean,
): CourseTaskRowActions {
  const sealed = isTaskSealed(task);
  return {
    edit: canManage && !sealed,
    seal: canManage && !sealed,
    restore: canManage && sealed,
    copy: true,
    remove: canManage,
  };
}
