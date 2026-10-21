import { describe, expect, it } from 'vitest';

import {
  getRequestErrorCode,
  getRequestErrorMessage,
  isGradingConflict,
} from './grading-errors';

describe('批改接口错误', () => {
  it('从业务错误里取编码与提示', () => {
    const conflict = {
      data: { code: 1_050_005_101, msg: '这份提交已被他人更新，请刷新后再看' },
    };
    expect(getRequestErrorCode(conflict)).toBe(1_050_005_101);
    expect(isGradingConflict(conflict)).toBe(true);
    expect(
      isGradingConflict({ response: { data: { code: 1_050_005_100 } } }),
    ).toBe(true);
    expect(isGradingConflict({ data: { code: 1_050_005_011 } })).toBe(false);
    expect(getRequestErrorMessage(conflict, '兜底')).toBe(
      '这份提交已被他人更新，请刷新后再看',
    );
  });

  it('网络错误没有编码，用兜底提示', () => {
    expect(getRequestErrorCode(new Error('Network Error'))).toBeUndefined();
    expect(isGradingConflict(undefined)).toBe(false);
    expect(
      getRequestErrorMessage(new Error('Network Error'), '批改没有保存'),
    ).toBe('批改没有保存');
  });
});
