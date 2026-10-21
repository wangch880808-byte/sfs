import { describe, expect, it, vi } from 'vitest';

import { useFormSchema } from './data';
import {
  checkHomeworkContext,
  HOMEWORK_COURSE_REQUIRED,
  HOMEWORK_INDICATOR_SECOND_REQUIRED,
  HOMEWORK_INDICATOR_UNSET_NOTICE,
  isHomeworkContextValid,
} from './form-validation';

// 表单定义里任务类型、等级的选项取自字典（要用 Pinia），这里只测规则
vi.mock('@vben/hooks', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vben/hooks')>()),
  getDictOptions: () => [],
}));

describe('新建任务：课程与指标', () => {
  it('没选课程时给出行内错误，不能进入预览', () => {
    for (const courseId of [undefined, null, '', 0, Number.NaN]) {
      const check = checkHomeworkContext({ courseId, indicatorFirstId: 1 });
      expect(check.courseError).toBe(HOMEWORK_COURSE_REQUIRED);
      expect(isHomeworkContextValid(check)).toBe(false);
    }
    expect(HOMEWORK_COURSE_REQUIRED).toBe('请选择课程');
  });

  it('选了课程即没有错误', () => {
    const check = checkHomeworkContext({
      courseId: 3,
      indicatorFirstId: 1,
      indicatorSecondId: 2,
    });
    expect(check).toEqual({
      courseError: '',
      indicatorSecondError: '',
      indicatorUnset: false,
    });
    expect(isHomeworkContextValid(check)).toBe(true);
  });

  it('一级指标不强制：没选也能进入预览，但标出来以便提醒', () => {
    const check = checkHomeworkContext({ courseId: 3 });
    expect(check.indicatorUnset).toBe(true);
    expect(check.indicatorSecondError).toBe('');
    expect(isHomeworkContextValid(check)).toBe(true);
    expect(HOMEWORK_INDICATOR_UNSET_NOTICE).toBe(
      '未选择能力指标，这次成绩不会计入学生的能力分析。',
    );
  });

  it('选了一级指标时二级指标必填', () => {
    const check = checkHomeworkContext({ courseId: 3, indicatorFirstId: 1 });
    expect(check.indicatorSecondError).toBe(HOMEWORK_INDICATOR_SECOND_REQUIRED);
    expect(isHomeworkContextValid(check)).toBe(false);
  });
});

/** 表单规则（zod）里这里用到的部分 */
interface ParseRule {
  isOptional: () => boolean;
  safeParse: (value: unknown) => {
    error?: { issues: Array<{ message: string }> };
    success: boolean;
  };
}

describe('新建任务：表单规则', () => {
  const schema = useFormSchema();
  const field = (name: string) => {
    const item = schema.find((entry) => entry.fieldName === name);
    if (!item) {
      throw new Error(`表单里没有 ${name}`);
    }
    return item;
  };

  it('班级必填：单选与多选都可以，空值、null 与空数组都提示「请选择班级」', () => {
    const rules = field('classId').rules as unknown as ParseRule;
    expect(rules.isOptional()).toBe(false);
    expect(rules.safeParse(5).success).toBe(true);
    expect(rules.safeParse([5, 6]).success).toBe(true);
    for (const value of [undefined, null, [], '']) {
      const result = rules.safeParse(value);
      expect(result.success).toBe(false);
      expect(result.error?.issues[0]?.message).toBe('请选择班级');
    }
  });

  it('二级指标只在选了一级指标时必填', () => {
    const dependencies = field('indicatorSecondId').dependencies ?? {
      triggerFields: [],
    };
    expect(dependencies.triggerFields).toEqual(['indicatorFirstId']);
    const rules = dependencies.rules as (
      values: Record<string, any>,
    ) => unknown;
    expect(rules({ indicatorFirstId: 1 })).toBe('selectRequired');
    expect(rules({ indicatorFirstId: undefined })).toBeNull();
  });
});
