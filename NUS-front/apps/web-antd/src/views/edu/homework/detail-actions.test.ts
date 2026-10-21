import { describe, expect, it } from 'vitest';

import {
  HOMEWORK_READ_ONLY_NOTICE,
  resolveHomeworkDetailActions,
} from './detail-actions';
import { HOMEWORK_VIEW_ONLY_NOTICE } from './grade-access';

const PUBLISHED = { deadline: '2026-10-20 23:59:00', status: 1 };

describe('任务详情页页头操作', () => {
  it('已发布、可改的任务：进入批改、个别延期、催交都能用', () => {
    expect(
      resolveHomeworkDetailActions(PUBLISHED, { readOnly: false }),
    ).toEqual({
      canCorrect: true,
      canExtend: true,
      canRemind: true,
      canViewSubmissions: false,
      notice: '',
      readOnly: false,
      viewOnly: false,
    });
    // 旧版后端不返回只读标记时照常可改
    expect(resolveHomeworkDetailActions(PUBLISHED, {}).readOnly).toBe(false);
    expect(resolveHomeworkDetailActions(PUBLISHED).canCorrect).toBe(true);
  });

  it('只读时（任务或班级、课程已封存）只留查看', () => {
    expect(resolveHomeworkDetailActions(PUBLISHED, { readOnly: true })).toEqual(
      {
        canCorrect: false,
        canExtend: false,
        canRemind: false,
        canViewSubmissions: false,
        notice: HOMEWORK_READ_ONLY_NOTICE,
        readOnly: true,
        viewOnly: false,
      },
    );
  });

  it('任务已封存时不能进入批改；还没读到任务时什么都不显示', () => {
    expect(
      resolveHomeworkDetailActions({ status: 3 }, { readOnly: false })
        .canCorrect,
    ).toBe(false);
    expect(resolveHomeworkDetailActions(undefined, undefined)).toEqual({
      canCorrect: false,
      canExtend: false,
      canRemind: false,
      canViewSubmissions: false,
      notice: '',
      readOnly: false,
      viewOnly: false,
    });
  });

  it('不是布置者（canGrade 为 false）：不显示批改、延期与催交，只能查看提交', () => {
    expect(
      resolveHomeworkDetailActions(
        { ...PUBLISHED, canGrade: false },
        { readOnly: false },
      ),
    ).toEqual({
      canCorrect: false,
      canExtend: false,
      canRemind: false,
      canViewSubmissions: true,
      notice: HOMEWORK_VIEW_ONLY_NOTICE,
      readOnly: false,
      viewOnly: true,
    });
    // 布置者照常
    expect(
      resolveHomeworkDetailActions({ ...PUBLISHED, canGrade: true }),
    ).toMatchObject({
      canCorrect: true,
      canExtend: true,
      canRemind: true,
      canViewSubmissions: false,
      viewOnly: false,
    });
  });

  it('不是布置者且任务已封存或只读：说明以封存为准，不再给查看提交入口', () => {
    const readOnly = resolveHomeworkDetailActions(
      { ...PUBLISHED, canGrade: false },
      { readOnly: true },
    );
    expect(readOnly.notice).toBe(HOMEWORK_READ_ONLY_NOTICE);
    expect(readOnly.canViewSubmissions).toBe(false);
    expect(
      resolveHomeworkDetailActions({ canGrade: false, status: 3 }, {})
        .canViewSubmissions,
    ).toBe(false);
  });

  it('只读说明写明原因和不能做的事', () => {
    expect(HOMEWORK_READ_ONLY_NOTICE).toContain('已封存');
    expect(HOMEWORK_READ_ONLY_NOTICE).not.toContain('归档');
    expect(HOMEWORK_READ_ONLY_NOTICE).toContain('不能再批改、打回或修改');
  });
});
