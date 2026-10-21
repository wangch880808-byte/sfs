import type { EduHomeworkDeadlineApi } from '#/api/edu/homework/deadline';
import type { DeadlineValue } from '#/utils/homework-deadline';

import dayjs from 'dayjs';

import { formatDeadlineTime, toDeadlineTime } from '#/utils/homework-deadline';
import { HOMEWORK_RECORD_STATUS, HOMEWORK_STATUS } from '#/utils/status';

/**
 * 任务详情页「个别延期」与「催交未提交学生」的呈现辅助：可选学生、默认时间、校验与结果提示。
 * 后端会再次校验，这里只负责把问题提前告诉教师。
 */

/** 提交格式，与后端 LocalDateTime 一致 */
export const EXTENSION_DEADLINE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 默认在 max(当前时间, 任务截止时间) 基础上顺延的天数 */
export const DEFAULT_EXTENSION_DAYS = 3;

/** 延期原因的长度上限（与后端校验一致） */
export const EXTENSION_REASON_MAX = 200;

export interface ExtensionStudentOption {
  label: string;
  value: number;
}

/** 学生显示名：姓名（学号）；缺姓名时用学号 */
export function formatStudentName(student: {
  studentId?: number;
  studentName?: string;
  studentNo?: string;
}) {
  const name = student.studentName?.trim();
  const no = student.studentNo?.trim();
  if (name && no) {
    return `${name}（${no}）`;
  }
  return name || no || `学生 ${student.studentId ?? ''}`.trim();
}

/** 只有还没提交的学生可以延期；已延期的在名字后面写上现在的个人截止时间 */
export function getExtensionCandidates(
  students: EduHomeworkDeadlineApi.Student[],
): ExtensionStudentOption[] {
  return students
    .filter(
      (student) =>
        Number(student.recordStatus) === HOMEWORK_RECORD_STATUS.NOT_SUBMITTED,
    )
    .map((student) => {
      const personal = formatDeadlineTime(student.personalDeadline, '');
      const name = formatStudentName(student);
      return {
        label: personal ? `${name} · 已延期到 ${personal}` : name,
        value: student.studentId,
      };
    });
}

/** 默认延期时间：max(当前时间, 任务截止时间) 顺延 3 天，精确到分钟 */
export function getDefaultExtensionDeadline(
  taskDeadline: DeadlineValue,
  now = Date.now(),
) {
  const base = Math.max(now, toDeadlineTime(taskDeadline) ?? now);
  return dayjs(base)
    .add(DEFAULT_EXTENSION_DAYS, 'day')
    .second(0)
    .millisecond(0)
    .format(EXTENSION_DEADLINE_FORMAT);
}

/** 延期时间要晚于任务截止时间和当前时间；返回问题说明，没有问题时返回空字符串 */
export function validateExtensionDeadline(
  value: DeadlineValue,
  taskDeadline: DeadlineValue,
  now = Date.now(),
) {
  const time = toDeadlineTime(value);
  if (time === undefined) {
    return '请选择延期后的截止时间';
  }
  const task = toDeadlineTime(taskDeadline);
  if (task !== undefined && time <= task) {
    return `延期后的截止时间要晚于任务截止时间 ${formatDeadlineTime(taskDeadline)}`;
  }
  if (time <= now) {
    return '延期后的截止时间要晚于当前时间';
  }
  return '';
}

/** 能否设置个别延期：已发布且有截止时间 */
export function canExtendHomework(homework?: {
  deadline?: DeadlineValue;
  status?: number;
}) {
  return (
    Number(homework?.status) === HOMEWORK_STATUS.PUBLISHED &&
    toDeadlineTime(homework?.deadline) !== undefined
  );
}

/** 能否催交：已发布 */
export function canRemindHomework(homework?: { status?: number }) {
  return Number(homework?.status) === HOMEWORK_STATUS.PUBLISHED;
}

export interface RemindFeedback {
  text: string;
  type: 'info' | 'success' | 'warning';
}

/** 催交结果提示 */
export function describeRemindResult(
  result?: EduHomeworkDeadlineApi.RemindResult,
): RemindFeedback {
  const target = Number(result?.targetCount ?? 0);
  const notified = Number(result?.notifiedCount ?? 0);
  if (target === 0) {
    return {
      text: '没有需要催交的学生：学生都已提交，或未提交的学生已过截止时间。',
      type: 'info',
    };
  }
  if (notified === 0) {
    return {
      text: '催交通知没有发出：通知服务暂时不可用。稍后再试。',
      type: 'warning',
    };
  }
  const next = formatDeadlineTime(result?.nextAvailableTime, '');
  const nextText = next ? `下次可在 ${next} 之后催交。` : '';
  if (notified < target) {
    return {
      text: `已提醒 ${notified} 名学生，另有 ${target - notified} 名没有发出。${nextText}`,
      type: 'warning',
    };
  }
  return { text: `已提醒 ${notified} 名学生。${nextText}`, type: 'success' };
}
