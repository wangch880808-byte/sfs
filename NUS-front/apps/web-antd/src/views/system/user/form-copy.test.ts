import { describe, expect, it } from 'vitest';

import { getUserFormTitle, INITIAL_PASSWORD_PLACEHOLDER } from './form-copy';

describe('用户表单标题与提示', () => {
  it('标题写调用方传入的对象名', () => {
    expect(getUserFormTitle(false, '学生')).toBe('新建学生');
    expect(getUserFormTitle(true, '教师')).toBe('编辑教师');
  });

  it('没传对象名时写「用户」', () => {
    expect(getUserFormTitle(false)).toBe('新建用户');
    expect(getUserFormTitle(true, '  ')).toBe('编辑用户');
  });

  it('初始密码提示写明长度与字符要求', () => {
    expect(INITIAL_PASSWORD_PLACEHOLDER).toBe('请输入初始密码（4–16 位）');
  });
});
