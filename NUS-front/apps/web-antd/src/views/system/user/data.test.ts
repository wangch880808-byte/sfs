import { describe, expect, it, vi } from 'vitest';

import { useFormSchema, useGridColumns } from './data';
import { INITIAL_PASSWORD_PLACEHOLDER } from './form-copy';

vi.mock('#/api/edu/class', () => ({ getSimpleClassList: vi.fn() }));
vi.mock('#/api/system/dept', () => ({ getDeptList: vi.fn() }));
vi.mock('#/api/system/role', () => ({ getSimpleRoleList: vi.fn() }));
vi.mock('@vben/hooks', () => ({ getDictOptions: () => [] }));
vi.mock('@vben/access', () => ({
  useAccess: () => ({ hasAccessByCodes: () => true }),
}));

describe('用户表单', () => {
  it('密码框提示初始密码的规则', () => {
    const password = useFormSchema().find(
      (item) => item.fieldName === 'password',
    );
    expect(
      (password?.componentProps as Record<string, unknown>)?.placeholder,
    ).toBe(INITIAL_PASSWORD_PLACEHOLDER);
  });
});

describe('用户列表', () => {
  it('手机号码后显示邮箱，教师、学生列表也沿用这一列', () => {
    const fields = (useGridColumns() ?? []).map((column) => column.field);
    expect(fields).toContain('email');
    expect(fields.indexOf('email')).toBe(fields.indexOf('mobile') + 1);
  });
});
