import { describe, expect, it } from 'vitest';

import { validateGrade } from './use-grading-selection';

describe('评分与评语校验', () => {
  it('评分必填、是 0～100 的整数', () => {
    expect(validateGrade(null, '好').score).toBe('请填写评分，0～100 分。');
    expect(validateGrade(101, '好').score).toBe('评分要在 0～100 之间。');
    expect(validateGrade(-1, '好').score).toBe('评分要在 0～100 之间。');
    expect(validateGrade(85.5, '好').score).toBe('评分要是整数。');
    expect(validateGrade(0, '好')).toEqual({ comment: '', score: '' });
    expect(validateGrade(100, '好')).toEqual({ comment: '', score: '' });
  });

  it('评语不能只有空白', () => {
    expect(validateGrade(80, '  ').comment).toBe(
      '请填写评语，学生会在反馈里看到。',
    );
  });
});
