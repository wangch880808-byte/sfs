import type { StatusMeta } from './status';

import type { StatusTone } from '#/styles/theme/palette';

import { getHomeworkRecordStatusMeta, HOMEWORK_RECORD_STATUS } from './status';

/**
 * 首页看板用到、utils/status.ts 里没有的状态呈现（V1.0.1.6 界面规范）：截止时间的紧迫程度，
 * 以及学生待完成任务的状态（前置等级没完成时为“未解锁”）。
 */

/** 离截止不到这么多小时算“即将截止” */
export const DUE_SOON_HOURS = 72;

const HOUR = 60 * 60 * 1000;

/** 离截止不到 DUE_SOON_HOURS 小时（还没过） */
export function isDueSoon(
  deadlineTime: number | undefined,
  now = Date.now(),
): boolean {
  return (
    deadlineTime !== undefined &&
    deadlineTime > now &&
    deadlineTime - now <= DUE_SOON_HOURS * HOUR
  );
}

/**
 * 截止时间的类别：已过为 failed（已逾期），不到 DUE_SOON_HOURS 小时为 waiting（即将截止），
 * 其余与没有截止时间为 neutral。
 */
export function getDeadlineTone(
  deadlineTime: number | undefined,
  now = Date.now(),
): StatusTone {
  if (deadlineTime === undefined) {
    return 'neutral';
  }
  if (deadlineTime <= now) {
    return 'failed';
  }
  return isDueSoon(deadlineTime, now) ? 'waiting' : 'neutral';
}

/** 学生待完成任务的状态：前置等级没完成时为“未解锁”，其余按提交记录状态（学生视角） */
export function getStudentTodoStatusMeta(item: {
  locked?: boolean;
  status?: null | number;
}): StatusMeta {
  if (item.locked) {
    return { label: '未解锁', tone: 'neutral' };
  }
  return getHomeworkRecordStatusMeta(
    item.status ?? HOMEWORK_RECORD_STATUS.NOT_SUBMITTED,
    'student',
  );
}
