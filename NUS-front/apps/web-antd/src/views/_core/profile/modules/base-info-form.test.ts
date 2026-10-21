import { describe, expect, it } from 'vitest';

import {
  normalizeProfileValues,
  profileEmailRule,
  profileMobileRule,
  profileSexRule,
} from './base-info-form';

describe('个人资料表单', () => {
  it('手机、邮箱、性别都可以不填', () => {
    for (const rule of [profileMobileRule, profileEmailRule, profileSexRule]) {
      expect(rule.isOptional()).toBe(true);
      expect(rule.safeParse(undefined).success).toBe(true);
      expect(rule.safeParse(null).success).toBe(true);
    }
    expect(profileMobileRule.safeParse('').success).toBe(true);
    expect(profileEmailRule.safeParse('').success).toBe(true);
  });

  it('填了才校验格式', () => {
    expect(profileMobileRule.safeParse('13800138000').success).toBe(true);
    expect(profileMobileRule.safeParse('1380013800').success).toBe(false);
    expect(profileMobileRule.safeParse('23800138000').success).toBe(false);
    expect(profileEmailRule.safeParse('teacher@example.com').success).toBe(
      true,
    );
    expect(profileEmailRule.safeParse('teacher@').success).toBe(false);
    expect(profileSexRule.safeParse(1).success).toBe(true);
  });

  it('提交前 null 不提交、空白提交为空字符串以便清空', () => {
    expect(
      normalizeProfileValues({
        email: '',
        mobile: '  ',
        nickname: '赵一鸣',
        sex: null,
      }),
    ).toEqual({
      email: '',
      mobile: '',
      nickname: '赵一鸣',
      sex: undefined,
    });
  });
});
