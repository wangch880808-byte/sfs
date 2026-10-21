import type { ClassFilterRow } from './class-filter';

import { describe, expect, it } from 'vitest';

import {
  buildClassOwnerOptions,
  clearedClassFilter,
  defaultClassFilter,
  filterClassRows,
  getClassOwnerKey,
  getClassOwnerLabel,
  hasHiddenAddedClasses,
  isClassOwnerUnclaimed,
  keepOwnerKey,
  UNCLAIMED_OWNER_KEY,
} from './class-filter';

const rows: ClassFilterRow[] = [
  {
    archived: false,
    classId: 1,
    className: '软件 2401',
    semesterName: '2026-2027-1',
    status: '进行中',
  },
  {
    archived: false,
    classId: 2,
    className: '软件 2402',
    semesterName: '2026-2027-2',
    status: '未开始',
  },
  {
    archived: true,
    classId: 3,
    className: '网络 2301',
    semesterName: '2026-2027-1',
    status: '进行中',
  },
  {
    archived: false,
    classId: 1,
    className: '软件 2401',
    semesterName: '2025-2026-2',
    status: '已结束',
  },
];

describe('课程详情：班级筛选', () => {
  it('默认只显示进行中且未封存的班级', () => {
    expect(
      filterClassRows(rows, defaultClassFilter()).map((r) => r.classId),
    ).toEqual([1]);
  });

  it('清除筛选后显示全部', () => {
    expect(filterClassRows(rows, clearedClassFilter())).toHaveLength(4);
  });

  it('关键词不区分大小写，学期按名称精确匹配', () => {
    expect(
      filterClassRows(rows, {
        keyword: ' 2402 ',
        semester: 'all',
        status: 'all',
      }).map((r) => r.classId),
    ).toEqual([2]);
    expect(
      filterClassRows(rows, {
        keyword: '',
        semester: '2026-2027-1',
        status: 'all',
      }).map((r) => r.classId),
    ).toEqual([1, 3]);
  });
});

describe('课程详情：添加班级后新班级是否被筛选挡住', () => {
  it('加到当前学期（进行中）：默认筛选下看得到', () => {
    expect(
      hasHiddenAddedClasses(rows, defaultClassFilter(), {
        classIds: [1],
        semesterName: '2026-2027-1',
      }),
    ).toBe(false);
  });

  it('加到未开始的学期：默认筛选下看不到', () => {
    expect(
      hasHiddenAddedClasses(rows, defaultClassFilter(), {
        classIds: [2],
        semesterName: '2026-2027-2',
      }),
    ).toBe(true);
    expect(
      hasHiddenAddedClasses(rows, clearedClassFilter(), {
        classIds: [2],
        semesterName: '2026-2027-2',
      }),
    ).toBe(false);
  });

  it('同一班级在别的学期的班课不算本次添加的', () => {
    // 班级 1 在 2025-2026-2 有已结束的班课，本次加的是 2026-2027-1 的
    expect(
      hasHiddenAddedClasses(rows, defaultClassFilter(), {
        classIds: [1],
        semesterName: ' 2026-2027-1 ',
      }),
    ).toBe(false);
  });

  it('关键词挡住了新班级也算看不到', () => {
    expect(
      hasHiddenAddedClasses(
        rows,
        { keyword: '网络', semester: 'all', status: 'all' },
        { classIds: [1], semesterName: '2026-2027-1' },
      ),
    ).toBe(true);
  });

  it('详情里没读到新班级时不提示', () => {
    expect(
      hasHiddenAddedClasses(rows, defaultClassFilter(), {
        classIds: [99],
        semesterName: '2026-2027-1',
      }),
    ).toBe(false);
  });
});

describe('课程详情：班级的负责人', () => {
  const ownerRows: ClassFilterRow[] = [
    {
      classId: 11,
      className: '软件 2401',
      ownerId: 7,
      ownerName: '王老师',
      semesterName: '2026-2027-1',
      status: '进行中',
    },
    {
      classId: 12,
      className: '软件 2402',
      ownerId: 5,
      ownerName: '李老师',
      semesterName: '2026-2027-1',
      status: '进行中',
    },
    {
      classId: 13,
      className: '软件 2403',
      ownerId: 7,
      ownerName: '王老师',
      semesterName: '2026-2027-1',
      status: '进行中',
    },
    {
      classId: 14,
      className: '软件 2404',
      ownerId: null,
      ownerName: '未认领',
      semesterName: '2026-2027-1',
      status: '进行中',
    },
    {
      classId: 15,
      className: '网络 2301',
      ownerId: 9,
      ownerName: '赵老师',
      semesterName: '2025-2026-2',
      status: '已结束',
    },
  ];

  it('负责人取值按编号；没有编号时按姓名；都没有时为未认领', () => {
    expect(getClassOwnerKey(ownerRows[0] as ClassFilterRow)).toBe('id:7');
    expect(getClassOwnerKey({ classId: 1, ownerName: '张老师' })).toBe(
      'name:张老师',
    );
    expect(getClassOwnerKey(ownerRows[3] as ClassFilterRow)).toBe(
      UNCLAIMED_OWNER_KEY,
    );
    // 旧版后端：只有认领人
    expect(
      getClassOwnerKey({
        classId: 1,
        claimTeacherId: 3,
        claimTeacherName: '周老师',
      }),
    ).toBe('id:3');
    expect(getClassOwnerKey({ classId: 1, claimTeacherId: null })).toBe(
      UNCLAIMED_OWNER_KEY,
    );
  });

  it('列里的文字：ownerName → claimTeacherName → 公共课「未认领」/专属课程「-」', () => {
    expect(getClassOwnerLabel(ownerRows[0] as ClassFilterRow, false)).toBe(
      '王老师',
    );
    expect(getClassOwnerLabel(ownerRows[3] as ClassFilterRow, true)).toBe(
      '未认领',
    );
    expect(
      getClassOwnerLabel({ classId: 1, claimTeacherName: '周老师' }, true),
    ).toBe('周老师');
    expect(getClassOwnerLabel({ classId: 1 }, true)).toBe('未认领');
    expect(getClassOwnerLabel({ classId: 1 }, false)).toBe('-');
  });

  it('只有公共课里未认领的班级弱化显示', () => {
    expect(isClassOwnerUnclaimed(ownerRows[3] as ClassFilterRow, true)).toBe(
      true,
    );
    expect(isClassOwnerUnclaimed(ownerRows[3] as ClassFilterRow, false)).toBe(
      false,
    );
    expect(isClassOwnerUnclaimed(ownerRows[0] as ClassFilterRow, true)).toBe(
      false,
    );
  });

  it('选项：所选学期内去重、按姓名排序，公共课最后多一个「未认领」', () => {
    expect(
      buildClassOwnerOptions(ownerRows, {
        claimMode: true,
        semester: '2026-2027-1',
      }),
    ).toEqual([
      { label: '李老师', value: 'id:5' },
      { label: '王老师', value: 'id:7' },
      { label: '未认领', value: UNCLAIMED_OWNER_KEY },
    ]);
    expect(
      buildClassOwnerOptions(ownerRows, { claimMode: false, semester: 'all' }),
    ).toEqual([
      { label: '李老师', value: 'id:5' },
      { label: '王老师', value: 'id:7' },
      { label: '赵老师', value: 'id:9' },
    ]);
  });

  it('按负责人筛选，「未认领」只留没有认领人的班级', () => {
    const base = clearedClassFilter();
    expect(
      filterClassRows(ownerRows, { ...base, ownerKey: 'id:7' }).map(
        (r) => r.classId,
      ),
    ).toEqual([11, 13]);
    expect(
      filterClassRows(ownerRows, {
        ...base,
        ownerKey: UNCLAIMED_OWNER_KEY,
      }).map((r) => r.classId),
    ).toEqual([14]);
    expect(filterClassRows(ownerRows, base)).toHaveLength(5);
  });

  it('清除筛选、重置筛选都清掉负责人', () => {
    expect(defaultClassFilter().ownerKey).toBeUndefined();
    expect(clearedClassFilter().ownerKey).toBeUndefined();
  });

  it('负责人筛选挡住了新认领的班级也算看不到', () => {
    const rowsWithMine: ClassFilterRow[] = [
      ...ownerRows,
      {
        classId: 16,
        className: '软件 2405',
        ownerId: 5,
        ownerName: '李老师',
        semesterName: '2026-2027-1',
        status: '进行中',
      },
    ];
    expect(
      hasHiddenAddedClasses(
        rowsWithMine,
        { ...clearedClassFilter(), ownerKey: 'id:7' },
        { classIds: [16], semesterName: '2026-2027-1' },
      ),
    ).toBe(true);
    expect(
      hasHiddenAddedClasses(
        rowsWithMine,
        { ...clearedClassFilter(), ownerKey: 'id:5' },
        { classIds: [16], semesterName: '2026-2027-1' },
      ),
    ).toBe(false);
  });

  it('选中的负责人不在选项里时清掉', () => {
    const options = [{ value: 'id:5' }];
    expect(keepOwnerKey('id:5', options)).toBe('id:5');
    expect(keepOwnerKey('id:7', options)).toBeUndefined();
    expect(keepOwnerKey(undefined, options)).toBeUndefined();
  });
});
