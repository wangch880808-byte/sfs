import dayjs from 'dayjs';

import { HOMEWORK_RECORD_STATUS } from './status';
import { isAwaitingSubmissionStatus } from './status-student-homework';

/** 后端时间字段：毫秒时间戳、秒级时间戳、数字字符串或日期字符串 */
export type DeadlineValue = null | number | string | undefined;

export interface HomeworkDeadlineFields {
  /** 学生能否提交（任务详情接口由后端计算） */
  canSubmit?: boolean;
  /** 任务截止时间 */
  deadline?: DeadlineValue;
  /** 有效截止时间（任务详情接口由后端计算） */
  effectiveDeadline?: DeadlineValue;
  /** 打回重做期限 */
  redoDeadline?: DeadlineValue;
  /** 提交状态：0 未提交、1 待批改、2 已批改、3 已打回、4 已重新提交 */
  status?: number;
}

/** 打回重做期限的提交格式 */
export const REDO_DEADLINE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 打回重做的默认期限：在 max(当前时间, 任务截止时间) 基础上顺延的天数 */
export const DEFAULT_REDO_EXTEND_DAYS = 7;

function isBlank(value: DeadlineValue) {
  return value === undefined || value === null || value === '';
}

/** 解析为毫秒时间戳；无法解析时返回 undefined */
export function toDeadlineTime(value: DeadlineValue): number | undefined {
  if (isBlank(value)) {
    return undefined;
  }
  const raw =
    typeof value === 'string' && /^\d+$/.test(value.trim())
      ? Number(value.trim())
      : value;
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) {
      return undefined;
    }
    // 兼容秒级时间戳
    return raw < 1e12 ? raw * 1000 : raw;
  }
  const parsed = dayjs(raw);
  return parsed.isValid() ? parsed.valueOf() : undefined;
}

/** 格式化为 YYYY-MM-DD HH:mm；无法解析时返回占位符 */
export function formatDeadlineTime(value: DeadlineValue, fallback = '-') {
  const time = toDeadlineTime(value);
  return time === undefined ? fallback : dayjs(time).format('YYYY-MM-DD HH:mm');
}

/** 有效截止时间：后端已计算时直接使用，否则打回重做期限优先于任务截止时间 */
export function resolveEffectiveDeadline(
  item: HomeworkDeadlineFields,
): DeadlineValue {
  if (!isBlank(item.effectiveDeadline)) {
    return item.effectiveDeadline;
  }
  return isBlank(item.redoDeadline) ? item.deadline : item.redoDeadline;
}

/** 是否以打回重做期限作为有效截止时间 */
export function isRedoDeadlineEffective(item: HomeworkDeadlineFields) {
  return (
    Number(item.status) === HOMEWORK_RECORD_STATUS.REDO &&
    !isBlank(item.redoDeadline)
  );
}

/** 是否已过有效截止时间；没有截止时间视为未截止 */
export function isDeadlinePassed(
  item: HomeworkDeadlineFields,
  now = Date.now(),
) {
  const time = toDeadlineTime(resolveEffectiveDeadline(item));
  return time !== undefined && now > time;
}

/**
 * 学生当前能否提交或重新提交：
 * 只有未提交（0）和已打回（3）可以提交；
 * 后端返回 canSubmit=false 或已过有效截止时间时不能提交。
 */
export function canSubmitHomework(
  item: HomeworkDeadlineFields,
  now = Date.now(),
) {
  if (item.canSubmit === false) {
    return false;
  }
  const status = Number(item.status ?? HOMEWORK_RECORD_STATUS.NOT_SUBMITTED);
  if (!isAwaitingSubmissionStatus(status)) {
    return false;
  }
  return !isDeadlinePassed(item, now);
}

/** 打回重做默认期限：max(当前时间, 任务截止时间) + 7 天（精确到分钟） */
export function getDefaultRedoDeadline(
  homeworkDeadline: DeadlineValue,
  now = Date.now(),
) {
  const base = Math.max(now, toDeadlineTime(homeworkDeadline) ?? now);
  return dayjs(base)
    .add(DEFAULT_REDO_EXTEND_DAYS, 'day')
    .second(0)
    .millisecond(0)
    .format(REDO_DEADLINE_FORMAT);
}

/** 重做期限必须晚于当前时间 */
export function isValidRedoDeadline(value: DeadlineValue, now = Date.now()) {
  const time = toDeadlineTime(value);
  return time !== undefined && time > now;
}
