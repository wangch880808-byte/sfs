import type { DeadlineValue } from '#/utils/homework-deadline';

import dayjs from 'dayjs';

import { toDeadlineTime } from '#/utils/homework-deadline';

/**
 * 看板上的时间写法（V1.0.1.6 界面规范）：列表里写相对时间，完整时间放在提示里。
 * 后端时间字段可能是毫秒、秒级时间戳、数字字符串或日期字符串，统一按 homework-deadline 的规则解析。
 */
export type TimeValue = Date | DeadlineValue;

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;

/** 解析为毫秒时间戳；无法解析时返回 undefined */
export function toTime(value: TimeValue): number | undefined {
  if (value instanceof Date) {
    const time = value.getTime();
    return Number.isNaN(time) ? undefined : time;
  }
  return toDeadlineTime(value);
}

/** 两个时间相差几个自然日（target 在 base 之后为正） */
export function calendarDayDiff(target: number, base: number): number {
  return dayjs(target).startOf('day').diff(dayjs(base).startOf('day'), 'day');
}

function formatDate(time: number, now: number): string {
  const date = dayjs(time);
  return date.year() === dayjs(now).year()
    ? date.format('M月D日')
    : date.format('YYYY年M月D日');
}

/** 完整时间（YYYY-MM-DD HH:mm），放在提示里；无法解析时返回空串 */
export function formatFullTime(value: TimeValue): string {
  const time = toTime(value);
  return time === undefined ? '' : dayjs(time).format('YYYY-MM-DD HH:mm');
}

/** 已经发生的时间：刚刚、N 分钟前、今天 HH:mm、昨天 HH:mm、M月D日（跨年带年份） */
export function formatPastTime(value: TimeValue, now = Date.now()): string {
  const time = toTime(value);
  if (time === undefined) {
    return '';
  }
  const diff = now - time;
  if (diff < MINUTE) {
    return '刚刚';
  }
  if (diff < HOUR) {
    return `${Math.floor(diff / MINUTE)} 分钟前`;
  }
  const days = calendarDayDiff(now, time);
  if (days === 0) {
    return `今天 ${dayjs(time).format('HH:mm')}`;
  }
  if (days === 1) {
    return `昨天 ${dayjs(time).format('HH:mm')}`;
  }
  return formatDate(time, now);
}

/** 截止时间：已截止、今天 HH:mm 截止、明天 HH:mm 截止、N 天后截止（一周内）、M月D日截止 */
export function formatDeadline(value: TimeValue, now = Date.now()): string {
  const time = toTime(value);
  if (time === undefined) {
    return '';
  }
  if (time <= now) {
    return '已截止';
  }
  const days = calendarDayDiff(time, now);
  if (days === 0) {
    return `今天 ${dayjs(time).format('HH:mm')} 截止`;
  }
  if (days === 1) {
    return `明天 ${dayjs(time).format('HH:mm')} 截止`;
  }
  if (days < 7) {
    return `${days} 天后截止`;
  }
  return `${formatDate(time, now)}截止`;
}

/** 提交后等了多久：刚提交、已等 N 小时、已等 N 天 */
export function formatWaiting(value: TimeValue, now = Date.now()): string {
  const time = toTime(value);
  if (time === undefined) {
    return '';
  }
  const diff = Math.max(0, now - time);
  if (diff < HOUR) {
    return '刚提交';
  }
  if (diff < 24 * HOUR) {
    return `已等 ${Math.floor(diff / HOUR)} 小时`;
  }
  return `已等 ${Math.floor(diff / (24 * HOUR))} 天`;
}

/** 过去的时间加动作：“今天 19:46 批改”“5 分钟前提交”“10月3日批改” */
export function formatPastAction(
  value: TimeValue,
  verb: string,
  now = Date.now(),
): string {
  const text = formatPastTime(value, now);
  if (!text) {
    return '';
  }
  return `${text}${/\d$/.test(text) ? ' ' : ''}${verb}`;
}
