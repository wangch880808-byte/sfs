import { describe, expect, it } from 'vitest';

import { parseCommonResultSseData } from './sse';

describe('parseCommonResultSseData: AI SSE business protocol', () => {
  it('returns successful stream content', () => {
    expect(parseCommonResultSseData('{"code":0,"data":"回答"}')).toBe('回答');
  });

  it('stops on a non-zero business code', () => {
    expect(() =>
      parseCommonResultSseData('{"code":1050010012,"msg":"AI 初评未启用"}'),
    ).toThrow('AI 初评未启用');
  });

  it('rejects malformed events', () => {
    expect(() => parseCommonResultSseData('not-json')).toThrow(
      'AI 返回的内容无法解析',
    );
  });
});
