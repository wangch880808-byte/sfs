import type { EduCourseManageApi } from '#/api/edu/course-manage';
import type { EduHomeworkApi } from '#/api/edu/homework';
import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';
import type { StatisticsApi } from '#/api/edu/statistics';
import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

import { getNotifyTargetUrl } from '#/utils/notify-message';
import { HOMEWORK_RECORD_STATUS, HOMEWORK_STATUS } from '#/utils/status';
import { DUE_SOON_HOURS, isDueSoon } from '#/utils/status-dashboard';

import { toTime } from './time';

/**
 * 教师工作台的数据整理（只整理现有接口的返回，不改口径）：
 * 任务列表 + 课程详情 → 任务索引；待批改记录按任务归组；即将截止、最近提交、学生留言、我的课程。
 */

/** 学生留言的站内信模板（utils/notify-message.ts 里的“收到学生留言”） */
export const TEACHER_MESSAGE_TEMPLATE = 'edu.teacher.message.created';

export interface TaskInfo {
  homeworkId: number;
  homeworkName: string;
  courseId?: number;
  courseName?: string;
  className?: string;
  /** 截止时间（毫秒） */
  deadline?: number;
  status: number;
  /** 班级学生数与已提交人数（来自课程详情，可能没有） */
  studentCount?: number;
  submittedCount?: number;
}

export interface TaskIndexSources {
  courseDetails?: EduCourseManageApi.CourseDetail[];
  courseOptions?: StatisticsApi.CourseOption[];
  homeworks?: EduHomeworkApi.Homework[];
}

/**
 * 我名下、正在进行的任务（已发布或已截止；任务列表已排除封存和失效的班课），按任务编号索引。
 * 任务列表给出名称、截止时间和状态；课程详情补上课程名、班级名和提交人数。
 */
export function buildTaskIndex({
  courseDetails = [],
  courseOptions = [],
  homeworks = [],
}: TaskIndexSources): Map<number, TaskInfo> {
  const courseNames = new Map(
    courseOptions.map((item) => [item.id, item.name]),
  );
  const classNames = new Map<number, string>();
  const courseTasks = new Map<number, EduCourseManageApi.CourseTask>();
  for (const detail of courseDetails) {
    courseNames.set(detail.courseId, detail.courseName);
    for (const item of detail.classes ?? []) {
      classNames.set(item.classId, item.className);
    }
    for (const task of detail.tasks ?? []) {
      courseTasks.set(task.homeworkId, task);
    }
  }

  const index = new Map<number, TaskInfo>();
  for (const homework of homeworks) {
    const status = Number(homework.status);
    if (
      !homework.id ||
      (status !== HOMEWORK_STATUS.PUBLISHED &&
        status !== HOMEWORK_STATUS.CLOSED)
    ) {
      continue;
    }
    const task = courseTasks.get(homework.id);
    index.set(homework.id, {
      homeworkId: homework.id,
      homeworkName: homework.homeworkName,
      courseId: homework.courseId,
      courseName: courseNames.get(homework.courseId),
      className: task?.className || classNames.get(homework.classId),
      deadline: toTime(homework.deadline),
      status,
      studentCount: task?.studentCount,
      submittedCount: task?.submittedCount,
    });
  }
  return index;
}

export interface PendingGroup {
  homeworkId: number;
  homeworkName: string;
  courseId?: number;
  courseName?: string;
  className?: string;
  /** 待批改份数（含重新提交） */
  count: number;
  /** 其中重新提交的份数 */
  resubmittedCount: number;
  /** 最早一份的提交时间（毫秒） */
  oldestSubmitTime?: number;
}

/**
 * 待批改记录（待批改与重新提交）按任务归组，只保留索引里的任务（与工作台的待批改数同一口径）；
 * 等得最久的任务排在前面，同样久的份数多的在前。
 */
export function groupPendingRecords(
  records: EduHomeworkRecordApi.HomeworkRecord[],
  index: Map<number, TaskInfo>,
): PendingGroup[] {
  const groups = new Map<number, PendingGroup>();
  for (const record of records) {
    const status = Number(record.status);
    const resubmitted = status === HOMEWORK_RECORD_STATUS.RESUBMITTED;
    if (status !== HOMEWORK_RECORD_STATUS.SUBMITTED && !resubmitted) {
      continue;
    }
    const task = index.get(record.homeworkId);
    if (!task) {
      continue;
    }
    let group = groups.get(task.homeworkId);
    if (!group) {
      group = {
        className: task.className,
        count: 0,
        courseId: task.courseId,
        courseName: task.courseName,
        homeworkId: task.homeworkId,
        homeworkName: task.homeworkName,
        resubmittedCount: 0,
      };
      groups.set(task.homeworkId, group);
    }
    group.count += 1;
    if (resubmitted) {
      group.resubmittedCount += 1;
    }
    const submitTime = toTime(record.submitTime);
    if (
      submitTime !== undefined &&
      (group.oldestSubmitTime === undefined ||
        submitTime < group.oldestSubmitTime)
    ) {
      group.oldestSubmitTime = submitTime;
    }
  }
  return [...groups.values()].toSorted(
    (a, b) =>
      (a.oldestSubmitTime ?? Number.POSITIVE_INFINITY) -
        (b.oldestSubmitTime ?? Number.POSITIVE_INFINITY) || b.count - a.count,
  );
}

/** 已发布、还没到截止时间的任务，按截止时间从近到远 */
export function pickUpcomingTasks(
  index: Map<number, TaskInfo>,
  now = Date.now(),
  limit = 5,
): TaskInfo[] {
  return [...index.values()]
    .filter(
      (task) =>
        task.status === HOMEWORK_STATUS.PUBLISHED &&
        task.deadline !== undefined &&
        task.deadline > now,
    )
    .toSorted((a, b) => (a.deadline ?? 0) - (b.deadline ?? 0))
    .slice(0, limit);
}

/** 已发布、离截止不到 72 小时的任务数 */
export function countDueSoon(
  index: Map<number, TaskInfo>,
  now = Date.now(),
): number {
  return [...index.values()].filter(
    (task) =>
      task.status === HOMEWORK_STATUS.PUBLISHED &&
      isDueSoon(task.deadline, now),
  ).length;
}

export interface RecentSubmission {
  recordId: number;
  homeworkId: number;
  homeworkName: string;
  studentName: string;
  status: number;
  submitTime: number;
}

/** 最近的提交：只保留索引里的任务，按提交时间从新到旧 */
export function pickRecentSubmissions(
  records: EduHomeworkRecordApi.HomeworkRecord[],
  index: Map<number, TaskInfo>,
  limit = 6,
): RecentSubmission[] {
  const items: RecentSubmission[] = [];
  for (const record of records) {
    const task = index.get(record.homeworkId);
    const submitTime = toTime(record.submitTime);
    if (!record.id || !task || submitTime === undefined) {
      continue;
    }
    items.push({
      homeworkId: task.homeworkId,
      homeworkName: task.homeworkName,
      recordId: record.id,
      status: Number(record.status),
      studentName: record.studentName || record.studentNo || '学生',
      submitTime,
    });
  }
  return items
    .toSorted((a, b) => b.submitTime - a.submitTime || b.recordId - a.recordId)
    .slice(0, limit);
}

export interface StudentMessageItem {
  id: number;
  studentName: string;
  courseName?: string;
  content: string;
  createTime?: number;
  targetUrl?: string;
}

function parseParams(
  value: SystemNotifyMessageApi.NotifyMessage['templateParams'],
): Record<string, unknown> {
  if (typeof value !== 'string') {
    return value ?? {};
  }
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function textOf(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/** 站内信里的“收到学生留言”，整理成学生、课程、留言内容和打开会话的地址 */
export function pickStudentMessages(
  list: SystemNotifyMessageApi.NotifyMessage[],
  limit = 5,
): StudentMessageItem[] {
  return list
    .filter((item) => item.templateCode === TEACHER_MESSAGE_TEMPLATE)
    .slice(0, limit)
    .map((item) => {
      const params = parseParams(item.templateParams);
      return {
        content: textOf(params.content) || textOf(item.templateContent),
        courseName: textOf(params.courseName) || undefined,
        createTime: toTime(item.createTime),
        id: item.id,
        studentName: textOf(params.studentName) || '学生',
        targetUrl: getNotifyTargetUrl(item),
      };
    });
}

export interface TeacherCourseItem {
  courseId: number;
  courseName: string;
  coverUrl?: string;
  /** 这门课待批改的份数（来自待批改记录；没读到时为 undefined） */
  pendingCount?: number;
  /** 班级、学生、任务数（来自课程详情；没读到时为 undefined） */
  classCount?: number;
  studentCount?: number;
  taskCount?: number;
}

/**
 * 我的课程：课程选项给出名单，课程详情补上班级（不含已封存）、学生和任务数；
 * 给了待批改分组时，按课程合计待批改份数。
 */
export function summarizeTeacherCourses(
  options: StatisticsApi.CourseOption[],
  details: EduCourseManageApi.CourseDetail[],
  pendingGroups?: PendingGroup[],
): TeacherCourseItem[] {
  const detailById = new Map(details.map((item) => [item.courseId, item]));
  const pendingOf = (courseId: number) =>
    pendingGroups
      ?.filter((group) => group.courseId === courseId)
      .reduce((sum, group) => sum + group.count, 0);
  return options.map((option) => {
    const detail = detailById.get(option.id);
    if (!detail) {
      return {
        courseId: option.id,
        courseName: option.name,
        pendingCount: pendingOf(option.id),
      };
    }
    const classes = (detail.classes ?? []).filter((item) => !item.archived);
    return {
      classCount: classes.length,
      courseId: option.id,
      courseName: detail.courseName || option.name,
      coverUrl: detail.courseImageUrl,
      pendingCount: pendingOf(option.id),
      studentCount: classes.reduce(
        (sum, item) => sum + Number(item.studentCount ?? 0),
        0,
      ),
      taskCount: (detail.tasks ?? []).length,
    };
  });
}

/** 页头说明：今天先要处理的事，没有就说没有 */
export function buildTeacherSummary(counts: {
  dueSoon?: number;
  pending?: number;
  unread?: number;
}): string {
  const parts: string[] = [];
  if (counts.pending) parts.push(`${counts.pending} 份提交待批改`);
  if (counts.unread) parts.push(`${counts.unread} 条留言未读`);
  if (counts.dueSoon) {
    parts.push(`${counts.dueSoon} 项任务 ${DUE_SOON_HOURS / 24} 天内截止`);
  }
  return parts.length > 0
    ? `${parts.join('，')}。`
    : '目前没有待批改的提交和未读留言。';
}
