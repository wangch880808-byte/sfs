import { describe, expect, it } from 'vitest';

import {
  getAccountStatusMeta,
  getArchiveStatusMeta,
  getNotifyReadMeta,
  getSecretConfiguredMeta,
} from './status-admin';

describe('班级封存状态', () => {
  it('状态值 1 为已封存（中性），其余为正常', () => {
    expect(getArchiveStatusMeta(1)).toEqual({
      label: '已封存',
      tone: 'neutral',
    });
    expect(getArchiveStatusMeta('1')).toEqual({
      label: '已封存',
      tone: 'neutral',
    });
    for (const status of [0, '0', null, undefined]) {
      expect(getArchiveStatusMeta(status)).toEqual({
        label: '正常',
        tone: 'done',
      });
    }
  });
});

describe('教务账号状态', () => {
  it('状态值 1 为已停用（中性），其余为正常', () => {
    expect(getAccountStatusMeta(1)).toEqual({
      label: '已停用',
      tone: 'neutral',
    });
    expect(getAccountStatusMeta(0)).toEqual({ label: '正常', tone: 'done' });
    expect(getAccountStatusMeta(undefined)).toEqual({
      label: '正常',
      tone: 'done',
    });
  });
});

describe('站内信与密钥', () => {
  it('未读等本人处理，已读为中性', () => {
    expect(getNotifyReadMeta(false)).toEqual({
      label: '未读',
      tone: 'waiting',
    });
    expect(getNotifyReadMeta(undefined).tone).toBe('waiting');
    expect(getNotifyReadMeta(true)).toEqual({ label: '已读', tone: 'neutral' });
  });

  it('密钥未配置等管理员处理', () => {
    expect(getSecretConfiguredMeta(true)).toEqual({
      label: '已配置',
      tone: 'done',
    });
    expect(getSecretConfiguredMeta(false)).toEqual({
      label: '未配置',
      tone: 'waiting',
    });
  });
});
