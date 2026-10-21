import { describe, expect, it } from 'vitest';

import {
  buildCourseClassOptions,
  QUICK_CREATE_MISSING_HINT,
  RELEASED_CLASS_SUFFIX,
  resolveQuickCreateBlockHint,
} from './class-options';

describe('任务表单「指定班级」的选项', () => {
  const rows = [
    { canOperate: true, classId: 1, className: '软件1班' },
    { canOperate: false, classId: 2, className: '软件2班' },
    { classId: 3 },
  ];

  it('只含能操作的班级；旧版后端没有 canOperate 时可选', () => {
    expect(buildCourseClassOptions(rows)).toEqual([
      { label: '软件1班', value: 1 },
      { label: '班级 3', value: 3 },
    ]);
  });

  it('修改任务时保留任务原来的班级', () => {
    expect(
      buildCourseClassOptions(rows, { classId: 2 }).map((item) => item.value),
    ).toEqual([1, 2, 3]);
  });

  it('公共课里原班级已不能操作时标「已放弃认领」', () => {
    expect(
      buildCourseClassOptions(rows, { classId: 2, publicCourse: true }),
    ).toContainEqual({ label: `软件2班${RELEASED_CLASS_SUFFIX}`, value: 2 });
    expect(RELEASED_CLASS_SUFFIX).toBe('（已放弃认领）');
  });

  it('原班级已不在列表里（放弃认领后）：用任务带的班级名称补一个选项', () => {
    expect(
      buildCourseClassOptions(rows, {
        classId: 7,
        className: '网络3班',
        publicCourse: true,
      }),
    ).toEqual([
      { label: '软件1班', value: 1 },
      { label: '班级 3', value: 3 },
      { label: '网络3班（已放弃认领）', value: 7 },
    ]);
  });

  it('没有班级名称（或是「-」）时退回编号；专属课程不标已放弃认领', () => {
    expect(
      buildCourseClassOptions([], {
        classId: 7,
        className: '-',
        publicCourse: true,
      }),
    ).toEqual([{ label: '班级 7（已放弃认领）', value: 7 }]);
    expect(
      buildCourseClassOptions([], { classId: 7, className: '网络3班' }),
    ).toEqual([{ label: '网络3班', value: 7 }]);
  });

  it('原班级仍能操作时不重复、不加标注', () => {
    expect(
      buildCourseClassOptions(rows, {
        classId: 1,
        className: '别的名字',
        publicCourse: true,
      }),
    ).toEqual([
      { label: '软件1班', value: 1 },
      { label: '班级 3', value: 3 },
    ]);
  });

  it('全部不能操作时没有选项', () => {
    expect(
      buildCourseClassOptions([{ canOperate: false, classId: 9 }]),
    ).toEqual([]);
  });
});

describe('从课程详情新建任务时不能打开表单的提示', () => {
  const base = { fixedCourseId: 1, fixedIndicatorFirstId: 2 };

  it('有班级时可以打开', () => {
    expect(
      resolveQuickCreateBlockHint({
        ...base,
        claimMode: true,
        relationCount: 1,
      }),
    ).toBe('');
  });

  it('公共课没有认领的班级：提示先认领', () => {
    expect(
      resolveQuickCreateBlockHint({
        ...base,
        claimMode: true,
        relationCount: 0,
      }),
    ).toContain('请先在课程详情里认领班级');
  });

  it('专属课程或没有传入公共课标记：保持原提示', () => {
    expect(
      resolveQuickCreateBlockHint({
        ...base,
        claimMode: false,
        relationCount: 0,
      }),
    ).toBe(QUICK_CREATE_MISSING_HINT);
    expect(resolveQuickCreateBlockHint({ ...base, relationCount: 0 })).toBe(
      QUICK_CREATE_MISSING_HINT,
    );
  });

  it('课程信息不全时即使是公共课也是原提示', () => {
    expect(
      resolveQuickCreateBlockHint({
        claimMode: true,
        fixedCourseId: 1,
        relationCount: 0,
      }),
    ).toBe(QUICK_CREATE_MISSING_HINT);
  });
});
