import { describe, expect, it } from 'vitest';

import {
  getHomeworkResultMeta,
  getLevelUnlockMeta,
  isAlreadySubmittedStatus,
  isAwaitingSubmissionStatus,
  isSubmittedStatus,
} from './status-student-homework';

describe('批改结果的达标情况', () => {
  it('已批改：成绩不低于及格线为已达标，否则未达标', () => {
    expect(
      getHomeworkResultMeta({ status: 2, myScore: 88, passScore: 60 }),
    ).toEqual({ label: '已达标', tone: 'done' });
    expect(
      getHomeworkResultMeta({ status: 2, myScore: 60, passScore: 60 }),
    ).toEqual({ label: '已达标', tone: 'done' });
    expect(
      getHomeworkResultMeta({ status: '2', myScore: '52', passScore: '60' }),
    ).toEqual({ label: '未达标', tone: 'failed' });
  });

  it('已批改但没有成绩或及格线时按未达标显示（与原页面一致）', () => {
    expect(getHomeworkResultMeta({ status: 2, passScore: 60 }).label).toBe(
      '未达标',
    );
    expect(getHomeworkResultMeta({ status: 2, myScore: 88 }).label).toBe(
      '未达标',
    );
  });

  it('已打回：还能重新提交时等学生处理，过了重做截止时间为失败', () => {
    expect(
      getHomeworkResultMeta({ status: 3, myScore: 52, canResubmit: true }),
    ).toEqual({ label: '待重新提交', tone: 'waiting' });
    expect(
      getHomeworkResultMeta({ status: 3, myScore: 52, canResubmit: false }),
    ).toEqual({ label: '已过重做截止时间', tone: 'failed' });
  });

  it('待批改与重新提交后都在等教师批改', () => {
    for (const status of [1, 4, null, undefined]) {
      expect(getHomeworkResultMeta({ status })).toEqual({
        label: '待教师批改',
        tone: 'running',
      });
    }
  });
});

describe('等级解锁', () => {
  it('已解锁为进行中，未解锁为中性', () => {
    expect(getLevelUnlockMeta(true)).toEqual({
      label: '已解锁',
      tone: 'running',
    });
    for (const value of [false, null, undefined]) {
      expect(getLevelUnlockMeta(value)).toEqual({
        label: '未解锁',
        tone: 'neutral',
      });
    }
  });
});

describe('学生端的提交状态判断', () => {
  const ALL = [0, 1, 2, 3, 4];
  const pick = (fn: (status: number) => boolean) =>
    ALL.filter((status) => fn(status));

  it('等学生提交：未提交与已打回', () => {
    expect(pick(isAwaitingSubmissionStatus)).toEqual([0, 3]);
  });

  it('提交过：1–4，与原来的 [1, 2, 3, 4].includes、status >= 1 一致', () => {
    expect(pick(isSubmittedStatus)).toEqual([1, 2, 3, 4]);
    expect(pick(isSubmittedStatus)).toEqual(ALL.filter((s) => s >= 1));
  });

  it('交上去了、没被打回：待批改、已批改、已重新提交', () => {
    expect(pick(isAlreadySubmittedStatus)).toEqual([1, 2, 4]);
  });

  it('空值、NaN 与不认识的取值都为 false', () => {
    for (const status of [null, undefined, Number.NaN, 5, -1]) {
      expect(isAwaitingSubmissionStatus(status)).toBe(false);
      expect(isSubmittedStatus(status)).toBe(false);
      expect(isAlreadySubmittedStatus(status)).toBe(false);
    }
  });
});
