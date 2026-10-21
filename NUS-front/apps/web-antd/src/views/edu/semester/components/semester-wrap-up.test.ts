import { describe, expect, it } from 'vitest';

import {
  defaultWrapUpSelection,
  defaultWrapUpSemester,
  hasOpenWork,
  resolveWrapUpLeave,
  wrapUpConfirmTitle,
  wrapUpResultText,
} from './semester-wrap-up';

describe('semester wrap-up helpers', () => {
  it('defaults to the latest previous semester that still has active links', () => {
    expect(
      defaultWrapUpSemester([
        { activeClassCourseCount: 10, current: true, semesterName: '本学期' },
        { activeClassCourseCount: 0, semesterName: '上学期' },
        { activeClassCourseCount: 3, semesterName: '前学期' },
      ]),
    ).toBe('前学期');
    expect(
      defaultWrapUpSemester([
        { activeClassCourseCount: 10, current: true, semesterName: '本学期' },
      ]),
    ).toBe('本学期');
    expect(defaultWrapUpSemester([])).toBeUndefined();
  });

  it('preselects only class courses without open work', () => {
    const rows = [
      { classCourseId: 1, openTaskCount: 0, pendingReviewCount: 0 },
      { classCourseId: 2, openTaskCount: 1, pendingReviewCount: 0 },
      { classCourseId: 3, openTaskCount: 0, pendingReviewCount: 2 },
    ];
    expect(defaultWrapUpSelection(rows)).toEqual([1]);
    expect(hasOpenWork(rows, [1])).toBe(false);
    expect(hasOpenWork(rows, [1, 3])).toBe(true);
  });

  it('writes the scope into the confirm title and result', () => {
    expect(wrapUpConfirmTitle('2025-2026 下学期', 12, 2)).toBe(
      '封存「2025-2026 下学期」的 12 个班级课程和 2 个班级？',
    );
    expect(wrapUpConfirmTitle('2025-2026 下学期', 0, 1)).toBe(
      '封存「2025-2026 下学期」的 1 个班级？',
    );
    expect(wrapUpResultText('2025-2026 下学期', 3, 0)).toBe(
      '「2025-2026 下学期」已收尾：封存了 3 个班级课程。',
    );
  });

  it('closes the dialog when leaving the page, but blocks leaving while archiving', () => {
    expect(resolveWrapUpLeave({ open: true, submitting: false })).toEqual({
      allowLeave: true,
      open: false,
    });
    expect(resolveWrapUpLeave({ open: false, submitting: false })).toEqual({
      allowLeave: true,
      open: false,
    });
    expect(resolveWrapUpLeave({ open: true, submitting: true })).toEqual({
      allowLeave: false,
      open: true,
    });
    // 弹窗已经关掉、请求还在路上时不拦着
    expect(resolveWrapUpLeave({ open: false, submitting: true })).toEqual({
      allowLeave: true,
      open: false,
    });
  });
});
