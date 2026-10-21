import { describe, expect, it } from 'vitest';

import {
  getAiPreReviewStatusMeta,
  getDictStatusTone,
  getDictStatusToneOf,
  getHomeworkRecordStatusMeta,
  getTaskProgressMeta,
  HOMEWORK_RECORD_STATUS,
  HOMEWORK_STATUS,
  isHomeworkArchived,
} from './status';

describe('提交记录状态', () => {
  it('教师视角：待批改与重新提交等教师处理，已打回在学生那里进行', () => {
    expect(
      getHomeworkRecordStatusMeta(HOMEWORK_RECORD_STATUS.SUBMITTED),
    ).toEqual({
      label: '待批改',
      tone: 'waiting',
    });
    expect(
      getHomeworkRecordStatusMeta(HOMEWORK_RECORD_STATUS.RESUBMITTED),
    ).toEqual({
      label: '已重新提交',
      tone: 'waiting',
    });
    expect(getHomeworkRecordStatusMeta(HOMEWORK_RECORD_STATUS.REDO)).toEqual({
      label: '已打回',
      tone: 'running',
    });
    expect(
      getHomeworkRecordStatusMeta(HOMEWORK_RECORD_STATUS.CORRECTED).tone,
    ).toBe('done');
    expect(
      getHomeworkRecordStatusMeta(HOMEWORK_RECORD_STATUS.NOT_SUBMITTED).tone,
    ).toBe('neutral');
  });

  it('学生视角：已打回等学生重做，提交后等教师批改', () => {
    expect(getHomeworkRecordStatusMeta(3, 'student')).toEqual({
      label: '已打回',
      tone: 'waiting',
    });
    expect(getHomeworkRecordStatusMeta(1, 'student')).toEqual({
      label: '待批改',
      tone: 'running',
    });
    expect(getHomeworkRecordStatusMeta(4, 'student')).toEqual({
      label: '待批改',
      tone: 'running',
    });
  });

  it('字符串状态按数字处理，空值与未知值显示未知状态', () => {
    expect(getHomeworkRecordStatusMeta('2').label).toBe('已批改');
    for (const value of [null, undefined, '', 9]) {
      expect(getHomeworkRecordStatusMeta(value)).toEqual({
        label: '未知状态',
        tone: 'neutral',
      });
    }
  });
});

describe('实训任务状态', () => {
  it('取值与 edu_homework.status 一致：0 待发布、1 已发布、2 已截止、3 已封存', () => {
    expect(HOMEWORK_STATUS).toEqual({
      DRAFT: 0,
      PUBLISHED: 1,
      CLOSED: 2,
      ARCHIVED: 3,
    });
  });

  it('已封存按数字判断，字符串取值也算；其余状态与空值都不是已封存', () => {
    expect(isHomeworkArchived(HOMEWORK_STATUS.ARCHIVED)).toBe(true);
    expect(isHomeworkArchived('3')).toBe(true);
    for (const status of [0, 1, 2, '1', null, undefined, '']) {
      expect(isHomeworkArchived(status)).toBe(false);
    }
  });
});

describe('aI 初评状态', () => {
  it('初评已生成等教师确认，生成失败标失败，没有记录为未生成', () => {
    expect(getAiPreReviewStatusMeta(2)).toEqual({
      label: '初评已生成',
      tone: 'waiting',
    });
    expect(getAiPreReviewStatusMeta(3).tone).toBe('failed');
    expect(getAiPreReviewStatusMeta(1).tone).toBe('running');
    expect(getAiPreReviewStatusMeta(4).tone).toBe('done');
    expect(getAiPreReviewStatusMeta(null)).toEqual({
      label: '未生成',
      tone: 'neutral',
    });
    expect(getAiPreReviewStatusMeta(42).label).toBe('未生成');
  });
});

describe('实训任务进度', () => {
  const now = new Date('2026-10-06T12:00:00+08:00').getTime();

  it('过了截止时间为已截止', () => {
    expect(
      getTaskProgressMeta(
        { deadline: '2026-10-05T12:00:00+08:00', submittedCount: 3 },
        now,
      ),
    ).toEqual({ label: '已截止', tone: 'neutral' });
  });

  it('提交都已批改为已完成，没有提交时仍在进行中', () => {
    expect(
      getTaskProgressMeta(
        {
          correctedCount: 3,
          deadline: '2026-10-09T12:00:00+08:00',
          submittedCount: 3,
        },
        now,
      ),
    ).toEqual({ label: '已完成', tone: 'done' });
    expect(
      getTaskProgressMeta({ correctedCount: 0, submittedCount: 0 }, now),
    ).toEqual({
      label: '进行中',
      tone: 'running',
    });
    expect(
      getTaskProgressMeta({ correctedCount: '1', submittedCount: '2' }, now)
        .tone,
    ).toBe('running');
  });
});

describe('字典颜色类型', () => {
  it('换成状态类别，其余为中性', () => {
    expect(getDictStatusTone('success')).toBe('done');
    expect(getDictStatusTone('warning')).toBe('waiting');
    expect(getDictStatusTone('danger')).toBe('failed');
    expect(getDictStatusTone('error')).toBe('failed');
    expect(getDictStatusTone('primary')).toBe('running');
    expect(getDictStatusTone('processing')).toBe('running');
    for (const value of ['info', 'default', '', null, undefined, '#ff0000']) {
      expect(getDictStatusTone(value)).toBe('neutral');
    }
  });
});

describe('字典值的呈现', () => {
  it('状态类字典按值给出类别', () => {
    expect(getDictStatusToneOf('common_status', 0)).toBe('done');
    expect(getDictStatusToneOf('common_status', '1')).toBe('neutral');
    expect(getDictStatusToneOf('infra_job_log_status', 2)).toBe('failed');
    expect(getDictStatusToneOf('edu_homework_status', 1)).toBe('running');
  });

  it('“*”兜底该字典其余取值；没指定的值按颜色类型', () => {
    expect(getDictStatusToneOf('system_login_result', 10)).toBe('failed');
    expect(getDictStatusToneOf('system_login_result', 0)).toBe('done');
    expect(getDictStatusToneOf('infra_job_status', 9, 'warning')).toBe(
      'waiting',
    );
  });

  it('颜色只是装饰的字典（性别、用户类型、是否）按普通文字显示', () => {
    expect(getDictStatusToneOf('system_user_sex', 2, 'success')).toBeNull();
    expect(getDictStatusToneOf('system_user_type', 1, 'error')).toBeNull();
    expect(
      getDictStatusToneOf('infra_boolean_string', 'true', 'danger'),
    ).toBeNull();
  });
});
