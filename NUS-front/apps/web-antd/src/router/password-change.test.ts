import { describe, expect, it } from 'vitest';

import {
  PASSWORD_CHANGE_PATH,
  shouldRedirectToPasswordChange,
} from './password-change';

describe('shouldRedirectToPasswordChange', () => {
  it('redirects every other page when password change is required', () => {
    expect(shouldRedirectToPasswordChange(true, '/')).toBe(true);
    expect(shouldRedirectToPasswordChange(true, '/student/profile')).toBe(true);
    expect(shouldRedirectToPasswordChange(true, '/auth/login')).toBe(true);
  });

  it('allows the password change page and normal sessions', () => {
    expect(shouldRedirectToPasswordChange(true, PASSWORD_CHANGE_PATH)).toBe(
      false,
    );
    expect(shouldRedirectToPasswordChange(false, '/student/profile')).toBe(
      false,
    );
  });
});
