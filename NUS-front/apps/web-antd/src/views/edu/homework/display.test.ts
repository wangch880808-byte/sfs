import { describe, expect, it } from 'vitest';

import { ARCHIVE_COPY } from '#/utils/archive';

import { GRADING_STRATEGY_OPTIONS } from './data';
import {
  CONFIRM_CANCEL_TEXT,
  formatDateTime,
  formatRate,
  gradingStrategyLabel,
  hasActiveFilters,
  HOMEWORK_ARCHIVE_HINT,
  HOMEWORK_PENDING_HINT,
  HOMEWORK_PENDING_LABEL,
  HOMEWORK_RESTORE_HINT,
  homeworkBatchConfirm,
  homeworkRowConfirm,
  isHomeworkPending,
  resolveIncludeArchived,
} from './display';

describe('确认框文案', () => {
  it('行内操作：标题写对象和动作，确认按钮重复动作名，取消按钮写“返回”', () => {
    expect(
      homeworkRowConfirm('封存', '组件化重构', HOMEWORK_ARCHIVE_HINT),
    ).toEqual({
      cancelText: CONFIRM_CANCEL_TEXT,
      description: `${ARCHIVE_COPY.archiveHint}。`,
      okText: '封存',
      title: '封存实训任务「组件化重构」？',
    });
    expect(homeworkRowConfirm('恢复', '', '说明').title).toBe(
      '恢复这个实训任务？',
    );
  });

  it('批量操作：标题写数量', () => {
    expect(homeworkBatchConfirm('封存', 3, HOMEWORK_ARCHIVE_HINT)).toEqual({
      cancelText: '返回',
      confirmText: '封存',
      content: HOMEWORK_ARCHIVE_HINT,
      title: '封存选中的 3 个实训任务？',
    });
  });

  it('任务列表的说明里不再出现“归档”“草稿”', () => {
    for (const text of [
      HOMEWORK_ARCHIVE_HINT,
      HOMEWORK_RESTORE_HINT,
      HOMEWORK_PENDING_HINT,
      HOMEWORK_PENDING_LABEL,
    ]) {
      expect(text).not.toMatch(/归档|草稿/);
    }
    expect(HOMEWORK_RESTORE_HINT).toBe(
      '恢复到封存前的状态，可以继续修改和批改。',
    );
  });
});

describe('待发布（旧草稿）', () => {
  it('状态 0 显示为待发布，提示编辑并保存后发布', () => {
    expect(isHomeworkPending(0)).toBe(true);
    expect(isHomeworkPending('0')).toBe(true);
    expect(HOMEWORK_PENDING_LABEL).toBe('待发布');
    expect(HOMEWORK_PENDING_HINT).toBe('编辑并保存后发布');
  });

  it('空值与其他状态不算待发布', () => {
    expect(isHomeworkPending(undefined)).toBe(false);
    expect(isHomeworkPending(null)).toBe(false);
    expect(isHomeworkPending('')).toBe(false);
    expect(isHomeworkPending(1)).toBe(false);
    expect(isHomeworkPending(3)).toBe(false);
  });
});

describe('列表是否带上已封存的任务', () => {
  it('开了「显示已封存」时带上', () => {
    expect(resolveIncludeArchived(undefined, true)).toBe(true);
    expect(resolveIncludeArchived(1, true)).toBe(true);
  });

  it('只在筛选里选了已封存、没开开关时也带上，避免结果永远为空', () => {
    expect(resolveIncludeArchived(3, false)).toBe(true);
    expect(resolveIncludeArchived('3', false)).toBe(true);
  });

  it('没开开关、也没选已封存时不带', () => {
    expect(resolveIncludeArchived(undefined, false)).toBe(false);
    expect(resolveIncludeArchived(null, false)).toBe(false);
    expect(resolveIncludeArchived('', false)).toBe(false);
    expect(resolveIncludeArchived(1, false)).toBe(false);
  });
});

describe('筛选条件是否生效', () => {
  it('空值、空白字符串与空数组不算筛选', () => {
    expect(hasActiveFilters(undefined)).toBe(false);
    expect(hasActiveFilters(null)).toBe(false);
    expect(
      hasActiveFilters({
        courseId: undefined,
        createTime: [],
        homeworkName: '  ',
        status: null,
      }),
    ).toBe(false);
    expect(hasActiveFilters({ createTime: [undefined, undefined] })).toBe(
      false,
    );
  });

  it('数字 0 与填了值的日期范围算筛选', () => {
    expect(hasActiveFilters({ homeworkLevel: 0 })).toBe(true);
    expect(hasActiveFilters({ status: 3 })).toBe(true);
    expect(hasActiveFilters({ homeworkName: '组件' })).toBe(true);
    expect(
      hasActiveFilters({ createTime: ['2026-10-01 00:00:00', undefined] }),
    ).toBe(true);
  });
});

describe('时间与比例', () => {
  it('空值显示“-”，秒与毫秒时间戳都显示到分钟', () => {
    expect(formatDateTime(undefined)).toBe('-');
    expect(formatDateTime(null)).toBe('-');
    expect(formatDateTime('')).toBe('-');
    const ms = new Date(2026, 9, 13, 19, 46, 30).getTime();
    expect(formatDateTime(ms)).toBe('2026-10-13 19:46');
    expect(formatDateTime(Math.floor(ms / 1000))).toBe('2026-10-13 19:46');
    expect(formatDateTime(String(ms))).toBe('2026-10-13 19:46');
    expect(formatDateTime('不是时间')).toBe('不是时间');
  });

  it('比例取整，总数为 0 时为 0%', () => {
    expect(formatRate(1, 3)).toBe('33%');
    expect(formatRate(3, 3)).toBe('100%');
    expect(formatRate(2, 0)).toBe('0%');
  });
});

describe('批改方式', () => {
  it('与新建表单的两种批改方式对应', () => {
    expect(gradingStrategyLabel(0, 0)).toBe('仅人工批改');
    expect(gradingStrategyLabel(undefined, 1)).toBe('仅人工批改');
    expect(gradingStrategyLabel(null, null)).toBe('仅人工批改');
    expect(gradingStrategyLabel(1, 1)).toBe('AI 初评 + 教师复核');
  });

  it('文字取自新建表单的批改方式选项，两处一致', () => {
    const labels = new Map(
      GRADING_STRATEGY_OPTIONS.map((item) => [item.value, item.label]),
    );
    expect(gradingStrategyLabel(0, 0)).toBe(labels.get('manual'));
    expect(gradingStrategyLabel(1, 1)).toBe(labels.get('assist'));
  });

  it('早期 autoGrade=1、aiReviewMode=0 的任务显示为 AI 初评 + 教师复核', () => {
    expect(gradingStrategyLabel(1, 0)).toBe('AI 初评 + 教师复核');
    expect(gradingStrategyLabel(1, undefined)).toBe('AI 初评 + 教师复核');
  });
});
