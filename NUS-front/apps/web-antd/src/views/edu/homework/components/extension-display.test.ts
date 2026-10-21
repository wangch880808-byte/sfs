import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  canExtendHomework,
  canRemindHomework,
  describeRemindResult,
  formatStudentName,
  getDefaultExtensionDeadline,
  getExtensionCandidates,
  validateExtensionDeadline,
} from './extension-display';

const NOW = dayjs('2026-10-10 10:00:00').valueOf();

describe('个别延期', () => {
  it('只列出还没提交的学生，已延期的写上个人截止时间', () => {
    const options = getExtensionCandidates([
      { recordStatus: 0, studentId: 1, studentName: '赵一鸣', studentNo: '01' },
      { recordStatus: 1, studentId: 2, studentName: '钱二', studentNo: '02' },
      {
        personalDeadline: '2026-10-12 18:00:00',
        recordStatus: 0,
        studentId: 3,
        studentName: '孙三',
        studentNo: '03',
      },
      { recordStatus: 3, studentId: 4, studentNo: '04' },
    ]);
    expect(options).toEqual([
      { label: '赵一鸣（01）', value: 1 },
      { label: '孙三（03） · 已延期到 2026-10-12 18:00', value: 3 },
    ]);
  });

  it('学生名缺失时用学号兜底', () => {
    expect(formatStudentName({ studentId: 5, studentNo: '2025005' })).toBe(
      '2025005',
    );
    expect(formatStudentName({ studentId: 5 })).toBe('学生 5');
  });

  it('默认时间在当前时间与任务截止时间中较晚的基础上顺延 3 天', () => {
    expect(getDefaultExtensionDeadline('2026-10-09 18:00:00', NOW)).toBe(
      '2026-10-13 10:00:00',
    );
    expect(getDefaultExtensionDeadline('2026-10-11 18:30:00', NOW)).toBe(
      '2026-10-14 18:30:00',
    );
  });

  it('延期时间要晚于任务截止时间和当前时间', () => {
    expect(validateExtensionDeadline(undefined, '2026-10-09', NOW)).toBe(
      '请选择延期后的截止时间',
    );
    expect(
      validateExtensionDeadline(
        '2026-10-11 18:00:00',
        '2026-10-11 18:00:00',
        NOW,
      ),
    ).toBe('延期后的截止时间要晚于任务截止时间 2026-10-11 18:00');
    expect(
      validateExtensionDeadline(
        '2026-10-10 09:00:00',
        '2026-10-09 18:00:00',
        NOW,
      ),
    ).toBe('延期后的截止时间要晚于当前时间');
    expect(
      validateExtensionDeadline(
        '2026-10-12 09:00:00',
        '2026-10-09 18:00:00',
        NOW,
      ),
    ).toBe('');
  });

  it('只有已发布、有截止时间的任务可以延期；已发布的任务可以催交', () => {
    expect(canExtendHomework({ deadline: '2026-10-09', status: 1 })).toBe(true);
    expect(canExtendHomework({ deadline: undefined, status: 1 })).toBe(false);
    expect(canExtendHomework({ deadline: '2026-10-09', status: 3 })).toBe(
      false,
    );
    expect(canRemindHomework({ status: 1 })).toBe(true);
    expect(canRemindHomework({ status: 0 })).toBe(false);
  });
});

describe('催交结果', () => {
  it('全部发出时写人数和下次可催交的时间', () => {
    expect(
      describeRemindResult({
        nextAvailableTime: '2026-10-10 11:00:00',
        notifiedCount: 3,
        targetCount: 3,
      }),
    ).toEqual({
      text: '已提醒 3 名学生。下次可在 2026-10-10 11:00 之后催交。',
      type: 'success',
    });
  });

  it('没有需要催交的学生', () => {
    expect(
      describeRemindResult({ notifiedCount: 0, targetCount: 0 }).type,
    ).toBe('info');
  });

  it('部分或全部没有发出时提示', () => {
    expect(describeRemindResult({ notifiedCount: 0, targetCount: 2 })).toEqual({
      text: '催交通知没有发出：通知服务暂时不可用。稍后再试。',
      type: 'warning',
    });
    expect(
      describeRemindResult({ notifiedCount: 1, targetCount: 3 }).text,
    ).toBe('已提醒 1 名学生，另有 2 名没有发出。');
  });
});
