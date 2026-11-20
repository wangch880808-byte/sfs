import type { StudentCourseApi } from '#/api/edu/student/course';
import type { StudentHomeApi } from '#/api/edu/student/home';
import type { StudentHomeworkApi } from '#/api/edu/student/homework';
import type { StudentConversation } from '#/api/edu/student/message';

import {
  canSubmitHomework,
  resolveEffectiveDeadline,
} from '#/utils/homework-deadline';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { DUE_SOON_HOURS, isDueSoon } from '#/utils/status-dashboard';

import { toTime } from './time';

/**
 * 学生首页的数据整理（只整理现有接口的返回，不改口径）：
 * 待完成（含已打回、未解锁）、最新反馈、未读留言、课程进度与页头说明。
 */

export interface TodoTask {
  homeworkId: number;
  homeworkName: string;
  courseId: number;
  courseName?: string;
  homeworkLevel: number;
  /** 提交记录状态：0 未提交、3 已打回 */
  status: number;
  /** 所在等级还没解锁 */
  locked: boolean;
  /** 有效截止时间（已打回时为重做期限，毫秒） */
  deadline?: number;
}

export interface TodoSummary {
  /** 还能提交的任务（含未解锁的），能做的在前，按截止时间从近到远 */
  items: TodoTask[];
  /** 没提交或被打回、但已过截止时间的任务数 */
  overdueCount: number;
}

const isTodoStatus = (status: number) =>
  status === HOMEWORK_RECORD_STATUS.NOT_SUBMITTED ||
  status === HOMEWORK_RECORD_STATUS.REDO;

/** 还有可提交任务的课程（需要读这些课程的等级解锁状态） */
export function collectTodoCourseIds(
  list: StudentHomeworkApi.HomeworkItem[],
  now = Date.now(),
): number[] {
  const ids = new Set<number>();
  for (const item of list) {
    if (isTodoStatus(Number(item.status)) && canSubmitHomework(item, now)) {
      ids.add(item.courseId);
    }
  }
  return [...ids];
}

/** 各课程还没解锁的等级 */
export function collectLockedLevels(
  entries: ReadonlyArray<
    readonly [number, StudentCourseApi.UnlockStatus | undefined]
  >,
): Map<number, Set<number>> {
  const locked = new Map<number, Set<number>>();
  for (const [courseId, status] of entries) {
    const levels = (status?.levels ?? [])
      .filter((level) => !level.unlocked)
      .map((level) => Number(level.level));
    if (levels.length > 0) {
      locked.set(courseId, new Set(levels));
    }
  }
  return locked;
}

/** 待完成：未提交与已打回、还没过有效截止时间的任务；所在等级没解锁的标为未解锁、排在后面。
 *  locked 直接用后端按“课程 + 一级指标（技能）+ 等级”匹配好的 item.locked，
 *  不再按“课程 + 等级”推断，否则会把另一个技能的锁算到这条任务上。 */
export function pickTodoTasks(
  list: StudentHomeworkApi.HomeworkItem[],
  now = Date.now(),
): TodoSummary {
  const items: TodoTask[] = [];
  let overdueCount = 0;
  for (const item of list) {
    const status = Number(item.status);
    if (!isTodoStatus(status)) {
      continue;
    }
    if (!canSubmitHomework(item, now)) {
      overdueCount += 1;
      continue;
    }
    items.push({
      courseId: item.courseId,
      courseName: item.courseName,
      deadline: toTime(resolveEffectiveDeadline(item)),
      homeworkId: item.homeworkId,
      homeworkLevel: Number(item.homeworkLevel),
      homeworkName: item.homeworkName,
      locked: item.locked === true,
      status,
    });
  }
  return {
    items: items.toSorted(
      (a, b) =>
        Number(a.locked) - Number(b.locked) ||
        (a.deadline ?? Number.POSITIVE_INFINITY) -
          (b.deadline ?? Number.POSITIVE_INFINITY) ||
        b.homeworkId - a.homeworkId,
    ),
    overdueCount,
  };
}

/** 能做的待完成任务里，离截止不到 72 小时的数量 */
export function countDueSoonTodos(todo: TodoSummary, now = Date.now()) {
  return todo.items.filter(
    (item) => !item.locked && isDueSoon(item.deadline, now),
  ).length;
}

export interface FeedbackItem {
  key: string;
  homeworkName: string;
  score: number;
  correctTime?: number;
  /** 能对上任务时给出，用来打开成绩反馈页 */
  homeworkId?: number;
}

/**
 * 最新反馈：首页概览的近期成绩（后端已按批改时间从新到旧）。概览不带任务编号，
 * 用任务名称在“已批改”的任务里找，唯一对上时才给出编号（打开成绩反馈页）。
 */
export function buildFeedbackItems(
  recentScores: StudentHomeApi.RecentScore[],
  list: StudentHomeworkApi.HomeworkItem[],
  limit = 5,
): FeedbackItem[] {
  const idsByName = new Map<string, Set<number>>();
  for (const item of list) {
    if (Number(item.status) !== HOMEWORK_RECORD_STATUS.CORRECTED) continue;
    const ids = idsByName.get(item.homeworkName) ?? new Set<number>();
    ids.add(item.homeworkId);
    idsByName.set(item.homeworkName, ids);
  }
  return recentScores.slice(0, limit).map((score, index) => {
    const ids = idsByName.get(score.homeworkName);
    return {
      correctTime: toTime(score.correctTime),
      homeworkId: ids?.size === 1 ? [...ids][0] : undefined,
      homeworkName: score.homeworkName,
      key: `${index}-${score.homeworkName}`,
      score: Number(score.score),
    };
  });
}

/** 各课程会话里老师回复的未读条数之和 */
export function sumUnread(conversations: StudentConversation[]): number {
  return conversations.reduce(
    (sum, item) => sum + Math.max(0, Number(item.unreadCount) || 0),
    0,
  );
}

/** 课程进度：已完成任务数 / 任务总数（百分比取整） */
export function getCourseProgress(course: {
  completedHomework?: number;
  totalHomework?: number;
}): { completed: number; percent: number; total: number } {
  const total = Math.max(0, Number(course.totalHomework) || 0);
  const completed = Math.min(
    total,
    Math.max(0, Number(course.completedHomework) || 0),
  );
  return {
    completed,
    percent: total > 0 ? Math.round((completed / total) * 100) : 0,
    total,
  };
}

/** 页头说明：待完成、即将截止、等待解锁与未读留言；没有就说没有 */
export function buildStudentSummary(
  { todo, unread }: { todo?: TodoSummary; unread?: number },
  now = Date.now(),
): string {
  const parts: string[] = [];
  if (todo) {
    const open = todo.items.filter((item) => !item.locked).length;
    const locked = todo.items.length - open;
    const dueSoon = countDueSoonTodos(todo, now);
    if (open > 0) {
      parts.push(`${open} 项任务待完成`);
      if (dueSoon > 0) {
        parts.push(`${dueSoon} 项 ${DUE_SOON_HOURS / 24} 天内截止`);
      }
      if (locked > 0) parts.push(`另有 ${locked} 项等待解锁`);
    } else if (locked > 0) {
      parts.push(`${locked} 项任务等待解锁，先完成上一等级的任务`);
    }
  }
  if (unread) parts.push(`${unread} 条留言未读`);
  return parts.length > 0 ? `${parts.join('，')}。` : '目前没有待完成的任务。';
}
