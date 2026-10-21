import { describe, expect, it } from 'vitest';

import {
  getLevelLockedDetail,
  LEVEL_LOCKED_CODE,
  resolveStudentLoadError,
  toSentence,
} from './load-error';

/** 请求库对业务错误的拒绝值：直接抛出响应体（request-client 抛 error.response.data） */
function businessError(code: number, msg: string) {
  return { code, data: null, msg };
}

/** 拦截器链里的写法：响应对象展开后再挂一份 response */
function interceptorError(code: number, msg: string) {
  const response = { data: { code, data: null, msg }, status: 200 };
  return { ...response, response };
}

describe('resolveStudentLoadError', () => {
  it('等级未解锁：用后端给出的原因，并补全句号', () => {
    expect(
      resolveStudentLoadError(
        businessError(
          LEVEL_LOCKED_CODE,
          '该等级尚未解锁，请先完成上一等级任务',
        ),
      ),
    ).toEqual({
      kind: 'locked',
      reason: '该等级尚未解锁，请先完成上一等级任务。',
    });
  });

  it('等级未解锁但没有原因时用默认说明', () => {
    expect(
      resolveStudentLoadError(businessError(LEVEL_LOCKED_CODE, '')),
    ).toEqual({
      kind: 'locked',
      reason: '该等级尚未解锁，请先完成上一等级任务。',
    });
  });

  it('课程或任务不存在、没有分配：不转述后端原话', () => {
    for (const code of [1_050_007_000, 1_050_008_000, 1_050_008_001]) {
      expect(
        resolveStudentLoadError(businessError(code, '您未被分配该任务')),
      ).toEqual({ kind: 'unavailable', reason: '' });
    }
  });

  it('响应体挂在 data 或 response.data 上时也能读出错误码', () => {
    expect(
      resolveStudentLoadError(interceptorError(LEVEL_LOCKED_CODE, '未解锁'))
        .kind,
    ).toBe('locked');
    expect(
      resolveStudentLoadError({
        response: { data: { code: '1050007001', msg: '未解锁' } },
      }).kind,
    ).toBe('locked');
  });

  it('其他业务错误带上原因；断网和超时写明原因；接口校验异常不带原因', () => {
    expect(resolveStudentLoadError(businessError(500, '系统异常'))).toEqual({
      kind: 'failed',
      reason: '系统异常。',
    });
    expect(
      resolveStudentLoadError(
        Object.assign(new Error('Network Error'), { code: 'ERR_NETWORK' }),
      ),
    ).toEqual({ kind: 'failed', reason: '网络连接中断。' });
    expect(
      resolveStudentLoadError(new Error('timeout of 30000ms exceeded')),
    ).toEqual({ kind: 'failed', reason: '服务器响应超时。' });
    expect(
      resolveStudentLoadError(new Error('学生课程接口缺少字段：courseId')),
    ).toEqual({ kind: 'failed', reason: '' });
    for (const value of [null, undefined, 'oops', { data: '<html>' }]) {
      expect(resolveStudentLoadError(value)).toEqual({
        kind: 'failed',
        reason: '',
      });
    }
  });
});

describe('未解锁等级的原因', () => {
  it('取对应等级的 reasonText；已解锁、没有该等级或旧版后端没有原因时为空', () => {
    const levels = [
      { level: 1, reasonText: '', unlocked: true },
      { level: 2, reasonText: '还差 2 个任务达标', unlocked: false },
      { level: 3, unlocked: false },
    ];
    expect(getLevelLockedDetail(levels, 2)).toBe('还差 2 个任务达标。');
    expect(getLevelLockedDetail(levels, 1)).toBe('');
    expect(getLevelLockedDetail(levels, 3)).toBe('');
    expect(getLevelLockedDetail(levels, 4)).toBe('');
    expect(getLevelLockedDetail(undefined, 2)).toBe('');
    expect(getLevelLockedDetail(levels, undefined)).toBe('');
  });
});

describe('toSentence', () => {
  it('句末没有标点时补全角句号，已有时不重复', () => {
    expect(toSentence(' 任务不存在 ')).toBe('任务不存在。');
    expect(toSentence('任务不存在。')).toBe('任务不存在。');
    expect(toSentence('')).toBe('');
  });
});
