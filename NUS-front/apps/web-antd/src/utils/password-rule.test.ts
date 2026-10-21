import { describe, expect, it } from 'vitest';

import { getNewPasswordError, getPasswordFormError } from './password-rule';

describe('password change validation', () => {
  it('accepts 8–16 character passwords containing letters and digits', () => {
    expect(getNewPasswordError('abc12345', 'old', 'student001')).toBeNull();
    expect(getNewPasswordError('abcdefgh12345678', 'old')).toBeNull();
  });

  it('rejects short, long, single-category and reused passwords', () => {
    expect(getNewPasswordError('abc1234', 'old')).not.toBeNull();
    expect(getNewPasswordError('abcdefgh123456789', 'old')).not.toBeNull();
    expect(getNewPasswordError('abcdefgh', 'old')).not.toBeNull();
    expect(getNewPasswordError('12345678', 'old')).not.toBeNull();
    expect(getNewPasswordError('abc12345', 'abc12345')).not.toBeNull();
    expect(getNewPasswordError('abc12345', 'old', 'abc12345')).not.toBeNull();
  });

  it('requires old password and matching confirmation', () => {
    expect(getPasswordFormError('', 'abc12345', 'abc12345')).toBe(
      '请输入旧密码',
    );
    expect(getPasswordFormError('old', 'abc12345', 'different')).toBe(
      '两次输入的新密码不一致',
    );
  });
});
