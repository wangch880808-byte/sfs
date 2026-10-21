import { describe, expect, it } from 'vitest';

import {
  getApiAccessResultMeta,
  getImportValidationMeta,
  getModelBindingMeta,
  getSocialBindingMeta,
} from './status-infra-ai';

describe('status-infra-ai', () => {
  it('访问日志结果：0 成功，其余错误码为失败，空值为未知', () => {
    expect(getApiAccessResultMeta(0)).toEqual({ label: '成功', tone: 'done' });
    expect(getApiAccessResultMeta('0')).toEqual({
      label: '成功',
      tone: 'done',
    });
    expect(getApiAccessResultMeta(500)).toEqual({
      label: '失败',
      tone: 'failed',
    });
    expect(getApiAccessResultMeta(null).tone).toBe('neutral');
    expect(getApiAccessResultMeta(undefined).label).toBe('未知');
    expect(getApiAccessResultMeta('').label).toBe('未知');
  });

  it('业务模型绑定：可用为已完成，未就绪等待处理', () => {
    expect(getModelBindingMeta(true)).toEqual({ label: '可用', tone: 'done' });
    expect(getModelBindingMeta(false)).toEqual({
      label: '未就绪',
      tone: 'waiting',
    });
  });

  it('第三方账号：已绑定为已完成，未绑定为中性', () => {
    expect(getSocialBindingMeta(true)).toEqual({
      label: '已绑定',
      tone: 'done',
    });
    expect(getSocialBindingMeta(false)).toEqual({
      label: '未绑定',
      tone: 'neutral',
    });
  });

  it('导入预检：通过、错误，其余为未知', () => {
    expect(getImportValidationMeta('VALID')).toEqual({
      label: '通过',
      tone: 'done',
    });
    expect(getImportValidationMeta('INVALID')).toEqual({
      label: '错误',
      tone: 'failed',
    });
    expect(getImportValidationMeta(undefined).tone).toBe('neutral');
    expect(getImportValidationMeta('PENDING').label).toBe('未知');
  });
});
