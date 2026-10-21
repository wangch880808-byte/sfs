import { describe, expect, it } from 'vitest';

import {
  formatAiDuration,
  formatAiTokens,
  getAiCallErrorLabel,
  getAiCallResultMeta,
  getPromptActiveVersionLabel,
  getPromptDefaultMeta,
  getPromptVersionMeta,
  sumAiCallDaily,
} from './status-ai-governance';

describe('status-ai-governance', () => {
  it('提示词版本：生效中为已完成，未启用为中性', () => {
    expect(getPromptVersionMeta(true)).toEqual({
      label: '生效中',
      tone: 'done',
    });
    expect(getPromptVersionMeta(false)).toEqual({
      label: '未启用',
      tone: 'neutral',
    });
  });

  it('与内置默认：一致为中性，改过为进行中', () => {
    expect(getPromptDefaultMeta(true)).toEqual({
      label: '与默认一致',
      tone: 'neutral',
    });
    expect(getPromptDefaultMeta(false)).toEqual({
      label: '已修改',
      tone: 'running',
    });
  });

  it('生效版本：没有启用版本时写内置默认', () => {
    expect(getPromptActiveVersionLabel(false, 3)).toBe('第 3 版');
    expect(getPromptActiveVersionLabel(true, 3)).toBe('内置默认');
    expect(getPromptActiveVersionLabel(false, undefined)).toBe('内置默认');
    expect(getPromptActiveVersionLabel(false, null)).toBe('内置默认');
  });

  it('调用结果：成功为已完成，失败为失败', () => {
    expect(getAiCallResultMeta(true)).toEqual({ label: '成功', tone: 'done' });
    expect(getAiCallResultMeta(false)).toEqual({
      label: '失败',
      tone: 'failed',
    });
  });

  it('失败类型只显示中文，认不出的归为其他，空值为空', () => {
    expect(getAiCallErrorLabel('TIMEOUT')).toBe('超时');
    expect(getAiCallErrorLabel('RATE_LIMITED')).toBe('服务商限流');
    expect(getAiCallErrorLabel('QUOTA_EXCEEDED')).toBe('已达用量上限');
    expect(getAiCallErrorLabel('CIRCUIT_OPEN')).toBe('熔断中');
    expect(getAiCallErrorLabel('SOMETHING_NEW')).toBe('其他');
    expect(getAiCallErrorLabel(undefined)).toBe('');
    expect(getAiCallErrorLabel(null)).toBe('');
    expect(getAiCallErrorLabel('')).toBe('');
  });

  it('令牌数：空值为「—」，0 照写，大数加千分位', () => {
    expect(formatAiTokens(null)).toBe('—');
    expect(formatAiTokens(undefined)).toBe('—');
    expect(formatAiTokens(0)).toBe('0');
    expect(formatAiTokens(12_345)).toBe('12,345');
  });

  it('耗时：毫秒换成秒，保留一位小数', () => {
    expect(formatAiDuration(3210)).toBe('3.2 秒');
    expect(formatAiDuration(0)).toBe('0.0 秒');
    expect(formatAiDuration(49)).toBe('0.0 秒');
    expect(formatAiDuration(60_000)).toBe('60.0 秒');
    expect(formatAiDuration(null)).toBe('—');
    expect(formatAiDuration(undefined)).toBe('—');
  });

  it('按天汇总的合计，缺的字段按 0 算', () => {
    expect(sumAiCallDaily(undefined)).toEqual({
      callCount: 0,
      failedCount: 0,
      totalTokens: 0,
    });
    expect(
      sumAiCallDaily([
        { callCount: 3, failedCount: 1, totalTokens: 900 },
        { callCount: 2, totalTokens: 100 },
      ]),
    ).toEqual({ callCount: 5, failedCount: 1, totalTokens: 1000 });
  });
});
