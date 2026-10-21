import type { ClassRowActionContext } from './class-claim';

import { describe, expect, it } from 'vitest';

import {
  buildClaimSelectOptions,
  canClaimClasses,
  CLAIM_CLASS_FIRST_HINT,
  CLAIM_VIEW_NOTICE_CLEARER,
  CLAIM_VIEW_NOTICE_OWNER,
  createLatestRequestGuard,
  filterOperableClasses,
  getClaimClassAbsenceCopy,
  getClaimOptionLabel,
  getClaimViewNotice,
  getClassRowActions,
  getCourseMetricCopy,
  isClaimMode,
  isClaimOptionDisabled,
  isClassOperable,
  resolveCanClearOthersClaim,
  resolveClaimAbility,
  sortClaimOptions,
} from './class-claim';

describe('认领弹窗：排序、灰显与文字', () => {
  const list = [
    {
      classId: 1,
      className: '软件2班',
      state: 'TAKEN' as const,
      claimTeacherName: '张三',
    },
    { classId: 2, className: '软件1班', state: 'MINE' as const },
    { classId: 3, className: '软件3班', state: 'AVAILABLE' as const },
    {
      classId: 4,
      className: '软件1班',
      state: 'TAKEN' as const,
      claimTeacherName: '李四',
    },
    { classId: 5, className: '电商1班', state: 'AVAILABLE' as const },
  ];

  it('可认领的在前，然后是已认领，最后是已被别人认领；同组按班级名', () => {
    expect(sortClaimOptions(list).map((item) => item.classId)).toEqual([
      5, 3, 2, 4, 1,
    ]);
    // 不改动原数组
    expect(list[0]?.classId).toBe(1);
  });

  it('只有可认领的能选', () => {
    expect(isClaimOptionDisabled({ classId: 1, state: 'AVAILABLE' })).toBe(
      false,
    );
    expect(isClaimOptionDisabled({ classId: 1, state: 'MINE' })).toBe(true);
    expect(isClaimOptionDisabled({ classId: 1, state: 'TAKEN' })).toBe(true);
  });

  it('已认领、已被别人认领的标明认领人', () => {
    expect(
      getClaimOptionLabel({
        classId: 1,
        className: '软件1班',
        state: 'AVAILABLE',
      }),
    ).toBe('软件1班');
    expect(
      getClaimOptionLabel({ classId: 1, className: '软件1班', state: 'MINE' }),
    ).toBe('软件1班（已认领）');
    expect(
      getClaimOptionLabel({
        claimTeacherName: '张三',
        classId: 1,
        className: '软件1班',
        state: 'TAKEN',
      }),
    ).toBe('软件1班（已被张三老师认领）');
    expect(
      getClaimOptionLabel({
        classId: 7,
        state: 'TAKEN',
        claimTeacherName: ' ',
      }),
    ).toBe('班级 7（已被其他老师认领）');
  });

  it('下拉选项排好序并带上 disabled', () => {
    expect(buildClaimSelectOptions(list)).toEqual([
      { disabled: false, label: '电商1班', value: 5 },
      { disabled: false, label: '软件3班', value: 3 },
      { disabled: true, label: '软件1班（已认领）', value: 2 },
      { disabled: true, label: '软件1班（已被李四老师认领）', value: 4 },
      { disabled: true, label: '软件2班（已被张三老师认领）', value: 1 },
    ]);
  });
});

describe('认领模式与可操作的班级', () => {
  it('只有 claimMode=true 才是认领模式，旧版后端按原来的样子', () => {
    expect(isClaimMode({ claimMode: true })).toBe(true);
    expect(isClaimMode({ claimMode: false })).toBe(false);
    expect(isClaimMode({})).toBe(false);
    expect(isClaimMode(undefined)).toBe(false);
  });

  it('只有 canOperate=false 才不能操作', () => {
    expect(isClassOperable({ canOperate: true })).toBe(true);
    expect(isClassOperable({})).toBe(true);
    expect(isClassOperable({ canOperate: false })).toBe(false);
  });

  it('任务表单的班级只含能操作的', () => {
    const rows = [
      { classId: 1, canOperate: true },
      { classId: 2, canOperate: false },
      { classId: 3 },
    ];
    expect(filterOperableClasses(rows).map((row) => row.classId)).toEqual([
      1, 3,
    ]);
  });

  it('认领按钮：公共课 + 权限码 + 教师或管理员', () => {
    const base = {
      claimMode: true,
      hasBindPermission: true,
      isTeacherOrAdmin: true,
    };
    expect(canClaimClasses(base)).toBe(true);
    expect(canClaimClasses({ ...base, claimMode: false })).toBe(false);
    expect(canClaimClasses({ ...base, hasBindPermission: false })).toBe(false);
    expect(canClaimClasses({ ...base, isTeacherOrAdmin: false })).toBe(false);
  });

  describe('认领按钮以课程详情的 canClaim 为准', () => {
    const base = {
      claimMode: true,
      hasBindPermission: true,
      isTeacherOrAdmin: true,
    };

    it('canClaim=true 时显示，不再看权限码与角色', () => {
      expect(
        resolveClaimAbility({
          ...base,
          canClaim: true,
          hasBindPermission: false,
          isTeacherOrAdmin: false,
        }),
      ).toEqual({ canClaim: true, courseOffline: false });
    });

    it('canClaim=false：权限码与角色都满足时原因是课程还没上线，否则是没有权限', () => {
      expect(resolveClaimAbility({ ...base, canClaim: false })).toEqual({
        canClaim: false,
        courseOffline: true,
      });
      expect(
        resolveClaimAbility({
          ...base,
          canClaim: false,
          hasBindPermission: false,
        }),
      ).toEqual({ canClaim: false, courseOffline: false });
    });

    it('旧版后端没有 canClaim 时退回权限码与角色的判断', () => {
      expect(resolveClaimAbility(base)).toEqual({
        canClaim: true,
        courseOffline: false,
      });
      expect(resolveClaimAbility({ ...base, isTeacherOrAdmin: false })).toEqual(
        { canClaim: false, courseOffline: false },
      );
    });

    it('不是公共课时不显示，也没有原因', () => {
      expect(
        resolveClaimAbility({ ...base, canClaim: true, claimMode: false }),
      ).toEqual({ canClaim: false, courseOffline: false });
    });
  });
});

describe('班级行上的操作', () => {
  const me = 10;
  const teacher: ClassRowActionContext = {
    canClearOthersClaim: false,
    canManageClasses: false,
    claimMode: true,
    currentUserId: me,
    hasBindPermission: true,
  };

  it('专属课程按原来的判断，没有认领相关操作', () => {
    const context = { ...teacher, canManageClasses: true, claimMode: false };
    expect(getClassRowActions({ archived: false }, context)).toEqual({
      archive: true,
      clearClaim: false,
      release: false,
      restore: false,
    });
    expect(getClassRowActions({ archived: true }, context)).toEqual({
      archive: false,
      clearClaim: false,
      release: false,
      restore: true,
    });
    expect(
      getClassRowActions(
        { archived: false },
        { ...context, canManageClasses: false },
      ).archive,
    ).toBe(false);
  });

  it('自己认领的行：放弃认领、封存；已封存的只能恢复', () => {
    expect(
      getClassRowActions(
        { archived: false, canOperate: true, claimTeacherId: me },
        teacher,
      ),
    ).toEqual({
      archive: true,
      clearClaim: false,
      release: true,
      restore: false,
    });
    expect(
      getClassRowActions(
        { archived: true, canOperate: true, claimTeacherId: me },
        teacher,
      ),
    ).toEqual({
      archive: false,
      clearClaim: false,
      release: false,
      restore: true,
    });
  });

  it('负责人看别人认领的行（canOperate=false）：不显示任何修改操作', () => {
    expect(
      getClassRowActions(
        { archived: false, canOperate: false, claimTeacherId: 20 },
        teacher,
      ),
    ).toEqual({
      archive: false,
      clearClaim: false,
      release: false,
      restore: false,
    });
    expect(
      getClassRowActions(
        { archived: false, canOperate: false, claimTeacherId: null },
        teacher,
      ),
    ).toEqual({
      archive: false,
      clearClaim: false,
      release: false,
      restore: false,
    });
  });

  it('管理员在别人认领的行上可以清除认领，未认领的行没有', () => {
    const admin = { ...teacher, canClearOthersClaim: true };
    expect(
      getClassRowActions(
        { archived: false, canOperate: true, claimTeacherId: '20' },
        admin,
      ),
    ).toEqual({
      archive: true,
      clearClaim: true,
      release: false,
      restore: false,
    });
    expect(
      getClassRowActions(
        { archived: false, canOperate: true, claimTeacherId: null },
        admin,
      ).clearClaim,
    ).toBe(false);
  });

  it('审批人（canOperate=false）也能清除别人的认领，但不能封存', () => {
    const auditor = { ...teacher, canClearOthersClaim: true };
    expect(
      getClassRowActions(
        { archived: false, canOperate: false, claimTeacherId: 20 },
        auditor,
      ),
    ).toEqual({
      archive: false,
      clearClaim: true,
      release: false,
      restore: false,
    });
  });

  it('没有绑定班级的权限码时不显示修改操作；读不到当前用户时不能放弃', () => {
    expect(
      getClassRowActions(
        { archived: false, canOperate: true, claimTeacherId: me },
        { ...teacher, hasBindPermission: false },
      ),
    ).toEqual({
      archive: false,
      clearClaim: false,
      release: false,
      restore: false,
    });
    expect(
      getClassRowActions(
        { archived: false, canOperate: true, claimTeacherId: me },
        { ...teacher, currentUserId: 0 },
      ).release,
    ).toBe(false);
  });

  describe('行上有 canRelease 时以它为准', () => {
    it('认领人是自己：显示放弃认领', () => {
      expect(
        getClassRowActions(
          {
            archived: false,
            canOperate: true,
            canRelease: true,
            claimTeacherId: me,
          },
          teacher,
        ),
      ).toEqual({
        archive: true,
        clearClaim: false,
        release: true,
        restore: false,
      });
    });

    it('认领人是别人：显示清除认领，不再看审批权限推断', () => {
      // 审批人：canOperate=false，但后端说能清除
      expect(
        getClassRowActions(
          {
            archived: false,
            canOperate: false,
            canRelease: true,
            claimTeacherId: 20,
          },
          teacher,
        ),
      ).toEqual({
        archive: false,
        clearClaim: true,
        release: false,
        restore: false,
      });
      // 有审批权限码，但后端说不能清除（如负责人兼有权限码但不是审批人）
      expect(
        getClassRowActions(
          {
            archived: false,
            canOperate: false,
            canRelease: false,
            claimTeacherId: 20,
          },
          { ...teacher, canClearOthersClaim: true },
        ),
      ).toEqual({
        archive: false,
        clearClaim: false,
        release: false,
        restore: false,
      });
    });

    it('canRelease=false 时自己认领的行也不显示放弃；已封存的行不显示', () => {
      expect(
        getClassRowActions(
          {
            archived: false,
            canOperate: true,
            canRelease: false,
            claimTeacherId: me,
          },
          teacher,
        ).release,
      ).toBe(false);
      expect(
        getClassRowActions(
          {
            archived: true,
            canOperate: true,
            canRelease: true,
            claimTeacherId: me,
          },
          teacher,
        ),
      ).toEqual({
        archive: false,
        clearClaim: false,
        release: false,
        restore: true,
      });
    });

    it('专属课程不看 canRelease', () => {
      expect(
        getClassRowActions(
          { archived: false, canRelease: true, claimTeacherId: me },
          { ...teacher, canManageClasses: true, claimMode: false },
        ).release,
      ).toBe(false);
    });
  });

  it('旧版后端没有 canOperate 时，公共课不显示封存（claimMode 也不会是 true）', () => {
    expect(
      getClassRowActions({ archived: false, claimTeacherId: me }, teacher)
        .archive,
    ).toBe(false);
  });
});

describe('文案', () => {
  it('看全部时的顶部提示：负责人与管理员不同，不看全部时没有', () => {
    expect(
      getClaimViewNotice({
        canClearOthersClaim: false,
        claimMode: true,
        viewAllReadOnly: true,
      }),
    ).toBe('你在查看全部班级，其他老师认领的班级只能查看');
    expect(CLAIM_VIEW_NOTICE_OWNER).toBe(
      '你在查看全部班级，其他老师认领的班级只能查看',
    );
    expect(
      getClaimViewNotice({
        canClearOthersClaim: true,
        claimMode: true,
        viewAllReadOnly: true,
      }),
    ).toBe('你在查看全部班级，可以清除教师的认领（例如教师离职）');
    expect(CLAIM_VIEW_NOTICE_CLEARER).toContain('例如教师离职');
    expect(
      getClaimViewNotice({
        canClearOthersClaim: false,
        claimMode: true,
        viewAllReadOnly: false,
      }),
    ).toBe('');
    expect(
      getClaimViewNotice({
        canClearOthersClaim: true,
        claimMode: false,
        viewAllReadOnly: true,
      }),
    ).toBe('');
  });

  it('指标卡：公共课普通教师改为「我认领的班级」，看全部与专属课程保持原文案', () => {
    expect(getCourseMetricCopy({ claimMode: true }).classLabel).toBe(
      '我认领的班级',
    );
    expect(
      getCourseMetricCopy({ claimMode: true, viewAllReadOnly: true })
        .classLabel,
    ).toBe('关联班级');
    expect(getCourseMetricCopy({ claimMode: false }).classLabel).toBe(
      '关联班级',
    );
  });

  it('没有班级时提示先认领', () => {
    const copy = getClaimClassAbsenceCopy(true);
    expect(copy.classEmpty).toContain('认领班级');
    expect(copy.createTaskHint).toContain(CLAIM_CLASS_FIRST_HINT);
    expect(copy.createAiTaskHint).toContain(CLAIM_CLASS_FIRST_HINT);
    expect(getClaimClassAbsenceCopy(false).classEmpty).toContain('权限');
    expect(getClaimClassAbsenceCopy(false, false).classEmpty).toContain('权限');
  });

  it('课程没上线时提示上线后再认领，不提示开通权限', () => {
    const copy = getClaimClassAbsenceCopy(false, true);
    expect(copy.classEmpty).toContain('课程上线后才能认领班级');
    expect(copy.classEmpty).not.toContain('权限');
  });
});

describe('看全部时能否清除别人的认领（区分负责人与管理员、审批人）', () => {
  const me = 10;
  const owner = {
    currentUserId: me,
    hasAuditPermission: false,
    isAdmin: false,
  };

  it('别人认领的行上 canRelease 普遍为 false：负责人，不能清除', () => {
    expect(
      resolveCanClearOthersClaim({
        ...owner,
        // 角色上即使有审批权限码，也以行上的字段为准
        hasAuditPermission: true,
        rows: [
          { canRelease: false, claimTeacherId: 20 },
          { canRelease: false, claimTeacherId: 21 },
          { canRelease: true, claimTeacherId: me },
        ],
      }),
    ).toBe(false);
  });

  it('别人认领的行上有 canRelease=true：管理员或审批人，能清除', () => {
    expect(
      resolveCanClearOthersClaim({
        ...owner,
        rows: [
          { canRelease: true, claimTeacherId: '20' },
          { canRelease: false, claimTeacherId: null },
        ],
      }),
    ).toBe(true);
  });

  it('没有别人认领的行、或旧版后端没有 canRelease 时按角色判断', () => {
    const rows = [
      { claimTeacherId: null },
      { canRelease: true, claimTeacherId: me },
      { claimTeacherId: 20 },
    ];
    expect(resolveCanClearOthersClaim({ ...owner, rows })).toBe(false);
    expect(resolveCanClearOthersClaim({ ...owner, isAdmin: true, rows })).toBe(
      true,
    );
    expect(
      resolveCanClearOthersClaim({ ...owner, hasAuditPermission: true, rows }),
    ).toBe(true);
  });

  it('与顶部提示组合：负责人与管理员看到不同的提示', () => {
    const notice = (canClearOthersClaim: boolean) =>
      getClaimViewNotice({
        canClearOthersClaim,
        claimMode: true,
        viewAllReadOnly: true,
      });
    expect(
      notice(
        resolveCanClearOthersClaim({
          ...owner,
          rows: [{ canRelease: false, claimTeacherId: 20 }],
        }),
      ),
    ).toBe(CLAIM_VIEW_NOTICE_OWNER);
    expect(
      notice(
        resolveCanClearOthersClaim({
          ...owner,
          isAdmin: true,
          rows: [{ canRelease: true, claimTeacherId: 20 }],
        }),
      ),
    ).toBe(CLAIM_VIEW_NOTICE_CLEARER);
  });
});

describe('只采用最后一次请求的结果', () => {
  it('后发先至时，先发的请求不再是最新的', () => {
    const guard = createLatestRequestGuard();
    const first = guard.next();
    const second = guard.next();
    expect(guard.isLatest(second)).toBe(true);
    expect(guard.isLatest(first)).toBe(false);
  });

  it('模拟快速切换学期：先发的慢请求返回时不覆盖', async () => {
    const guard = createLatestRequestGuard();
    let options: string[] = [];
    const load = async (result: string[], delay: number) => {
      const seq = guard.next();
      await new Promise((resolve) => setTimeout(resolve, delay));
      if (guard.isLatest(seq)) {
        options = result;
      }
    };
    await Promise.all([load(['旧学期班级'], 20), load(['新学期班级'], 0)]);
    expect(options).toEqual(['新学期班级']);
  });
});
