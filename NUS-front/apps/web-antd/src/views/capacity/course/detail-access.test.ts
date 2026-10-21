import { describe, expect, it } from 'vitest';

import {
  canManageCourseClasses,
  canManageCourseTask,
  CLASS_COURSE_BIND_PERMISSION,
  getClassAbsenceCopy,
  getCourseTaskGradeLabel,
} from './detail-access';

function accessWith(codes: string[]) {
  return (required: string[]) => required.some((code) => codes.includes(code));
}

describe('课程详情：添加班级、封存与恢复班课', () => {
  it('能管理这门课并且有绑定班级的权限码才显示', () => {
    expect(
      canManageCourseClasses(
        { canManage: true },
        accessWith([CLASS_COURSE_BIND_PERMISSION]),
      ),
    ).toBe(true);
  });

  it('有权限码但不能管理这门课（不是负责人或团队成员）时不显示', () => {
    const hasCode = accessWith([CLASS_COURSE_BIND_PERMISSION]);
    expect(canManageCourseClasses({ canManage: false }, hasCode)).toBe(false);
    // 旧版后端没有 canManage，按不能管理处理
    expect(canManageCourseClasses({}, hasCode)).toBe(false);
    expect(canManageCourseClasses(undefined, hasCode)).toBe(false);
  });

  it('能管理这门课但没有绑定班级的权限码时不显示', () => {
    expect(
      canManageCourseClasses(
        { canManage: true },
        accessWith(['edu:class-course:unbind']),
      ),
    ).toBe(false);
  });

  it('按权限码 edu:class-course:bind 判断', () => {
    expect(CLASS_COURSE_BIND_PERMISSION).toBe('edu:class-course:bind');
  });
});

describe('课程还没有班级时的文案', () => {
  it('能管理的人：去「班级」里添加', () => {
    const copy = getClassAbsenceCopy(true);
    expect(copy.taskBlocked).toContain('先在「班级」里把班级添加到本课程');
    expect(copy.createTaskHint).toContain('再新建任务');
    expect(Object.values(copy).join('')).not.toContain('联系');
  });

  it('不能管理的人：联系课程负责人或管理员，不让他去点不存在的按钮', () => {
    const copy = getClassAbsenceCopy(false);
    for (const text of Object.values(copy)) {
      expect(text).toContain('请联系课程负责人或管理员添加班级');
      expect(text).not.toContain('先在「班级」里');
    }
  });
});

describe('课程详情：实训任务行的操作', () => {
  it('canManage 为 false（不是布置者）时隐藏编辑、封存等修改类操作', () => {
    expect(canManageCourseTask({ canManage: false })).toBe(false);
  });

  it('canManage 为 true 或旧版后端没有这个字段时保持显示', () => {
    expect(canManageCourseTask({ canManage: true })).toBe(true);
    expect(canManageCourseTask({})).toBe(true);
    expect(canManageCourseTask(undefined)).toBe(true);
  });

  it('canGrade 为 false 时批改按钮改为「查看提交」', () => {
    expect(getCourseTaskGradeLabel({ canGrade: false })).toBe('查看提交');
  });

  it('canGrade 为 true 或旧版后端没有这个字段时仍为「批改」', () => {
    expect(getCourseTaskGradeLabel({ canGrade: true })).toBe('批改');
    expect(getCourseTaskGradeLabel({})).toBe('批改');
    expect(getCourseTaskGradeLabel(undefined)).toBe('批改');
  });

  it('已封存的任务只读，批改按钮改为「查看提交」', () => {
    expect(getCourseTaskGradeLabel({ canGrade: true, status: 3 })).toBe(
      '查看提交',
    );
    expect(getCourseTaskGradeLabel({ canGrade: true, status: 1 })).toBe('批改');
  });

  it('两个字段互不影响', () => {
    const task = { canGrade: true, canManage: false };
    expect(canManageCourseTask(task)).toBe(false);
    expect(getCourseTaskGradeLabel(task)).toBe('批改');
  });
});
