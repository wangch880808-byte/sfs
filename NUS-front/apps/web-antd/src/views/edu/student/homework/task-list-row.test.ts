import { describe, expect, it } from 'vitest';

import { HOMEWORK_RECORD_STATUS } from '#/utils/status';

import {
  getTaskListAction,
  getTaskListStatusMeta,
  getTaskUnlockPath,
  isTaskLocked,
} from './task-list-row';

const NOW = new Date('2026-10-08T12:00:00+08:00').getTime();
const FUTURE = '2026-10-20 23:59:59';

function row(overrides: Record<string, unknown> = {}) {
  return {
    courseId: 12,
    deadline: FUTURE,
    homeworkId: 34,
    status: HOMEWORK_RECORD_STATUS.NOT_SUBMITTED,
    ...overrides,
  };
}

describe('任务中心：未解锁的任务', () => {
  it('状态写「未解锁」，操作改为「查看解锁条件」，打开课程详情', () => {
    const item = row({ locked: true, lockReason: '初级任务达标后解锁' });
    expect(isTaskLocked(item)).toBe(true);
    expect(getTaskListStatusMeta(item)).toEqual({
      label: '未解锁',
      tone: 'neutral',
    });
    expect(getTaskListAction(item, NOW)).toEqual({
      kind: 'unlock',
      label: '查看解锁条件',
      tooltip: '初级任务达标后解锁',
    });
    expect(getTaskUnlockPath(item)).toBe('/student/course/detail/12');
  });

  it('没有原因时不带悬浮提示', () => {
    expect(getTaskListAction(row({ locked: true }), NOW)).toEqual({
      kind: 'unlock',
      label: '查看解锁条件',
    });
    expect(
      getTaskListAction(row({ locked: true, lockReason: '  ' }), NOW).tooltip,
    ).toBeUndefined();
  });

  it('已打回的任务所在等级没解锁，同样按未解锁处理', () => {
    const item = row({ locked: true, status: HOMEWORK_RECORD_STATUS.REDO });
    expect(getTaskListStatusMeta(item).label).toBe('未解锁');
    expect(getTaskListAction(item, NOW).kind).toBe('unlock');
  });

  it('即使截止时间未过、能提交，锁定时也不显示「去提交」', () => {
    const item = row({ canSubmit: true, locked: true });
    expect(getTaskListAction(item, NOW).label).not.toBe('去提交');
  });

  it('往期任务与已经交过的任务不按未解锁处理', () => {
    const history = row({ historyOnly: true, locked: true });
    expect(isTaskLocked(history)).toBe(false);
    expect(getTaskListAction(history, NOW)).toEqual({
      kind: 'view',
      label: '查看',
    });
    const corrected = row({
      locked: true,
      status: HOMEWORK_RECORD_STATUS.CORRECTED,
    });
    expect(isTaskLocked(corrected)).toBe(false);
    expect(getTaskListStatusMeta(corrected).label).not.toBe('未解锁');
    expect(getTaskListAction(corrected, NOW).kind).toBe('view');
  });

  it('缺课程编号时没有解锁条件的链接', () => {
    expect(getTaskUnlockPath(row({ courseId: undefined }))).toBeUndefined();
  });
});

describe('任务中心：已解锁的任务', () => {
  it('未提交、没过截止时间：去提交', () => {
    const item = row({ locked: false });
    expect(getTaskListStatusMeta(item).label).not.toBe('未解锁');
    expect(getTaskListAction(item, NOW)).toEqual({
      kind: 'submit',
      label: '去提交',
    });
  });

  it('旧版后端没有 locked 时按已解锁处理', () => {
    expect(getTaskListAction(row(), NOW).kind).toBe('submit');
  });

  it('过了截止时间或已提交：查看', () => {
    expect(
      getTaskListAction(row({ deadline: '2026-10-01 00:00:00' }), NOW).kind,
    ).toBe('view');
    expect(
      getTaskListAction(row({ status: HOMEWORK_RECORD_STATUS.SUBMITTED }), NOW)
        .kind,
    ).toBe('view');
  });
});
