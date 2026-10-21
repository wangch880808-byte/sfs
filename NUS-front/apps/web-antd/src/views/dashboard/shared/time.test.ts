import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  calendarDayDiff,
  formatDeadline,
  formatFullTime,
  formatPastAction,
  formatPastTime,
  formatWaiting,
  toTime,
} from './time';

const at = (value: string) => dayjs(value).valueOf();
const NOW = at('2026-10-06 20:00:00');

describe('看板时间：解析', () => {
  it('接受毫秒、秒级时间戳、日期字符串与 Date，无法解析时为 undefined', () => {
    const time = at('2026-10-06 19:46:00');
    expect(toTime(time)).toBe(time);
    expect(toTime(time / 1000)).toBe(time);
    expect(toTime('2026-10-06 19:46:00')).toBe(time);
    expect(toTime(new Date(time))).toBe(time);
    expect(toTime(undefined)).toBeUndefined();
    expect(toTime('')).toBeUndefined();
    expect(toTime(new Date('无效'))).toBeUndefined();
  });

  it('自然日差按日历算，不按 24 小时算', () => {
    expect(calendarDayDiff(at('2026-10-07 00:10:00'), NOW)).toBe(1);
    expect(calendarDayDiff(at('2026-10-06 23:59:00'), NOW)).toBe(0);
    expect(calendarDayDiff(at('2026-10-05 08:00:00'), NOW)).toBe(-1);
  });

  it('完整时间用于提示', () => {
    expect(formatFullTime('2026-10-06 19:46:09')).toBe('2026-10-06 19:46');
    expect(formatFullTime(undefined)).toBe('');
  });
});

describe('看板时间：已经发生的时间', () => {
  it('一分钟内为刚刚，一小时内写分钟，同一天写今天，前一天写昨天', () => {
    expect(formatPastTime(NOW - 20 * 1000, NOW)).toBe('刚刚');
    expect(formatPastTime(NOW - 5 * 60 * 1000, NOW)).toBe('5 分钟前');
    expect(formatPastTime(at('2026-10-06 08:05:00'), NOW)).toBe('今天 08:05');
    expect(formatPastTime(at('2026-10-05 22:30:00'), NOW)).toBe('昨天 22:30');
  });

  it('更早的写日期，跨年带年份；时钟偏差导致的未来时间按刚刚处理', () => {
    expect(formatPastTime(at('2026-09-28 10:00:00'), NOW)).toBe('9月28日');
    expect(formatPastTime(at('2025-12-30 10:00:00'), NOW)).toBe(
      '2025年12月30日',
    );
    expect(formatPastTime(NOW + 30 * 1000, NOW)).toBe('刚刚');
    expect(formatPastTime(undefined, NOW)).toBe('');
  });

  it('加动作时数字后面空一格', () => {
    expect(formatPastAction(at('2026-10-06 18:46:00'), '批改', NOW)).toBe(
      '今天 18:46 批改',
    );
    expect(formatPastAction(NOW - 5 * 60 * 1000, '提交', NOW)).toBe(
      '5 分钟前提交',
    );
    expect(formatPastAction(at('2026-10-03 09:00:00'), '批改', NOW)).toBe(
      '10月3日批改',
    );
    expect(formatPastAction(undefined, '批改', NOW)).toBe('');
  });
});

describe('看板时间：截止时间', () => {
  it('已过为已截止，今天、明天写到分钟，一周内写天数，更远写日期', () => {
    expect(formatDeadline(NOW - 1000, NOW)).toBe('已截止');
    expect(formatDeadline(at('2026-10-06 23:30:00'), NOW)).toBe(
      '今天 23:30 截止',
    );
    expect(formatDeadline(at('2026-10-07 08:00:00'), NOW)).toBe(
      '明天 08:00 截止',
    );
    expect(formatDeadline(at('2026-10-09 08:00:00'), NOW)).toBe('3 天后截止');
    expect(formatDeadline(at('2026-10-13 19:46:04'), NOW)).toBe('10月13日截止');
    expect(formatDeadline(at('2027-01-05 08:00:00'), NOW)).toBe(
      '2027年1月5日截止',
    );
    expect(formatDeadline(null, NOW)).toBe('');
  });
});

describe('看板时间：等了多久', () => {
  it('不到一小时为刚提交，不到一天写小时，其余写天', () => {
    expect(formatWaiting(NOW - 10 * 60 * 1000, NOW)).toBe('刚提交');
    expect(formatWaiting(NOW - 3 * 60 * 60 * 1000, NOW)).toBe('已等 3 小时');
    expect(formatWaiting(NOW - 50 * 60 * 60 * 1000, NOW)).toBe('已等 2 天');
    expect(formatWaiting(NOW + 1000, NOW)).toBe('刚提交');
    expect(formatWaiting(undefined, NOW)).toBe('');
  });
});
