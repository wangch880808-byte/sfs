import { describe, expect, it } from 'vitest';

import {
  createErrorToastDeduper,
  getGlobalErrorKey,
  GLOBAL_ERROR_DEDUPE_MS,
} from './error-toast';

describe('global error toast dedupe', () => {
  it('shows the same text once within the window', () => {
    let now = 1000;
    const shouldShow = createErrorToastDeduper(3000, () => now);
    expect(shouldShow('网络异常')).toBe(true);
    now += 500;
    expect(shouldShow('网络异常')).toBe(false);
    now += 2000;
    expect(shouldShow(' 网络异常 ')).toBe(false);
  });

  it('shows the text again after the window passes', () => {
    let now = 0;
    const shouldShow = createErrorToastDeduper(3000, () => now);
    expect(shouldShow('服务暂时不可用')).toBe(true);
    now = 3000;
    expect(shouldShow('服务暂时不可用')).toBe(true);
  });

  it('does not suppress different texts', () => {
    const shouldShow = createErrorToastDeduper(3000, () => 0);
    expect(shouldShow('甲')).toBe(true);
    expect(shouldShow('乙')).toBe(true);
  });

  it('ignores blank text', () => {
    const shouldShow = createErrorToastDeduper();
    expect(shouldShow('   ')).toBe(false);
  });

  it('uses a stable key and a few-seconds default window', () => {
    expect(getGlobalErrorKey(' 网络异常 ')).toBe('global-error:网络异常');
    expect(GLOBAL_ERROR_DEDUPE_MS).toBeGreaterThanOrEqual(2000);
    expect(GLOBAL_ERROR_DEDUPE_MS).toBeLessThanOrEqual(5000);
  });
});
