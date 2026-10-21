import { describe, expect, it } from 'vitest';

import { getGreeting, getGreetingTitle } from './greeting';

const atHour = (hour: number) => new Date(2026, 9, 6, hour, 30);

describe('首页问候', () => {
  it('按时段问候', () => {
    expect(getGreeting(atHour(2))).toBe('夜深了');
    expect(getGreeting(atHour(7))).toBe('早安');
    expect(getGreeting(atHour(10))).toBe('上午好');
    expect(getGreeting(atHour(12))).toBe('中午好');
    expect(getGreeting(atHour(15))).toBe('下午好');
    expect(getGreeting(atHour(21))).toBe('晚上好');
  });

  it('标题只写问候与名字，用全角逗号；没有名字时只写问候', () => {
    expect(getGreetingTitle('王敏', atHour(21))).toBe('晚上好，王敏');
    expect(getGreetingTitle('  李晓雨 ', atHour(10))).toBe('上午好，李晓雨');
    expect(getGreetingTitle(undefined, atHour(21))).toBe('晚上好');
    expect(getGreetingTitle('', atHour(21))).toBe('晚上好');
    expect(getGreetingTitle('王敏', atHour(21))).not.toMatch(/[!！,]/);
  });
});
