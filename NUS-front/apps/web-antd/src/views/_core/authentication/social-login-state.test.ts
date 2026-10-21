import { describe, expect, it } from 'vitest';

import {
  AUTH_THIRD_LOGIN_NOT_BIND_CODE,
  shouldShowSocialBindForm,
} from './social-login-state';

describe('shouldShowSocialBindForm', () => {
  it('仅未绑定账号时显示绑定表单', () => {
    expect(
      shouldShowSocialBindForm({
        response: { data: { code: AUTH_THIRD_LOGIN_NOT_BIND_CODE } },
      }),
    ).toBe(true);
    expect(
      shouldShowSocialBindForm({
        data: { code: AUTH_THIRD_LOGIN_NOT_BIND_CODE },
      }),
    ).toBe(true);
  });

  it('配置停用、授权失败和网络异常时不显示绑定表单', () => {
    expect(
      shouldShowSocialBindForm({ response: { data: { code: 1_002_018_214 } } }),
    ).toBe(false);
    expect(
      shouldShowSocialBindForm({ response: { data: { code: 1_002_018_000 } } }),
    ).toBe(false);
    expect(shouldShowSocialBindForm(new Error('Network Error'))).toBe(false);
    expect(shouldShowSocialBindForm(null)).toBe(false);
  });
});
