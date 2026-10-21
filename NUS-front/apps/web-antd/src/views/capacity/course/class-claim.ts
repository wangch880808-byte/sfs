/**
 * 公共课教师认领班级（V1.0.1.10）。
 * 公共课（课程详情 claimMode=true）里，班级由教师各自认领：教师只看、只管自己认领的班级；
 * 课程负责人、审批人、管理员能看全部（viewAllReadOnly=true），但别人认领的班级只能查看，管理员、审批人可以清除认领。
 * 后端在班级行上给出 canOperate（能否布置任务、放弃认领、封存）。专属课程，或旧版后端没有这些字段时，一切保持原来的样子。
 */

/** 认领弹窗里班级的状态：可认领、自己已认领、已被别人认领 */
export type ClaimState = 'AVAILABLE' | 'MINE' | 'TAKEN';

/** 认领弹窗只用到班级的这几个字段 */
export interface ClaimOptionRow {
  classId: number;
  className?: string;
  claimTeacherName?: null | string;
  state: ClaimState;
}

/** 可认领的在前，然后是自己已认领的，最后是已被别人认领的；未知状态排最后 */
const CLAIM_STATE_ORDER: Record<ClaimState, number> = {
  AVAILABLE: 0,
  MINE: 1,
  TAKEN: 2,
};

function claimStateOrder(state: string | undefined): number {
  return CLAIM_STATE_ORDER[state as ClaimState] ?? 3;
}

/** 按状态分组排序，同组按班级名排序（后端已排好，前端再排一次保证灰显的在下面） */
export function sortClaimOptions<T extends ClaimOptionRow>(list: T[]): T[] {
  return list.toSorted(
    (a, b) =>
      claimStateOrder(a.state) - claimStateOrder(b.state) ||
      (a.className ?? '').localeCompare(b.className ?? '', 'zh-CN'),
  );
}

/** 只有可认领的班级能选；自己已认领、已被别人认领的灰显 */
export function isClaimOptionDisabled(option: ClaimOptionRow): boolean {
  return option.state !== 'AVAILABLE';
}

/** 下拉里的文字：已认领、已被别人认领的在班级名后面标明 */
export function getClaimOptionLabel(option: ClaimOptionRow): string {
  const name = option.className || `班级 ${option.classId}`;
  if (option.state === 'MINE') {
    return `${name}（已认领）`;
  }
  if (option.state === 'TAKEN') {
    const teacher = option.claimTeacherName?.trim();
    return teacher
      ? `${name}（已被${teacher}老师认领）`
      : `${name}（已被其他老师认领）`;
  }
  return name;
}

export interface ClaimSelectOption {
  disabled: boolean;
  label: string;
  value: number;
}

/** 认领弹窗的下拉选项：排好序、灰显不可选的 */
export function buildClaimSelectOptions(
  list: ClaimOptionRow[],
): ClaimSelectOption[] {
  return sortClaimOptions(list).map((item) => ({
    disabled: isClaimOptionDisabled(item),
    label: getClaimOptionLabel(item),
    value: Number(item.classId),
  }));
}

/** 课程详情是否公共课认领模式；旧版后端没有 claimMode 时按原来的样子 */
export function isClaimMode(detail?: { claimMode?: boolean }): boolean {
  return detail?.claimMode === true;
}

/** 班级行能否操作（布置任务等）：只有后端明确说不能（false）时才不能，旧版后端没有字段时按能操作 */
export function isClassOperable(row?: { canOperate?: boolean }): boolean {
  return row?.canOperate !== false;
}

/** 新建任务、AI 新建任务可选的班级：只含能操作的班级（负责人看全部时也只能选自己认领的） */
export function filterOperableClasses<T extends { canOperate?: boolean }>(
  rows: T[],
): T[] {
  return rows.filter((row) => isClassOperable(row));
}

/** 公共课里能否显示「认领班级」（旧版后端的推断）：有绑定班级的权限码，并且是教师或管理员 */
export function canClaimClasses(options: {
  claimMode: boolean;
  hasBindPermission: boolean;
  isTeacherOrAdmin: boolean;
}): boolean {
  return (
    options.claimMode && options.hasBindPermission && options.isTeacherOrAdmin
  );
}

export interface ClaimAbility {
  canClaim: boolean;
  /** 不能认领是因为课程还没上线（有权限、是教师或管理员）；为 false 时不能认领的原因是没有权限 */
  courseOffline: boolean;
}

/**
 * 公共课里能否认领班级。
 * 以课程详情顶层的 canClaim 为准（后端已判断课程在线、绑定班级的权限码、教师或管理员）；
 * 旧版后端没有 canClaim 时按权限码与角色推断（判断不了课程是否上线，读认领列表时后端会提示）。
 * 后端说不能、但权限码与角色都满足时，原因是课程还没上线。
 */
export function resolveClaimAbility(options: {
  canClaim?: boolean;
  claimMode: boolean;
  hasBindPermission: boolean;
  isTeacherOrAdmin: boolean;
}): ClaimAbility {
  if (!options.claimMode) {
    return { canClaim: false, courseOffline: false };
  }
  const inferred = canClaimClasses(options);
  if (typeof options.canClaim === 'boolean') {
    return {
      canClaim: options.canClaim,
      courseOffline: !options.canClaim && inferred,
    };
  }
  return { canClaim: inferred, courseOffline: false };
}

/** 班级行上判断操作要用到的字段 */
export interface ClaimClassRow {
  archived?: boolean;
  canOperate?: boolean;
  /**
   * 该行已被认领，且当前用户能放弃（认领人本人）或清除（管理员、审批人）认领。
   * 旧版后端没有这个字段，按 canOperate 与角色推断。
   */
  canRelease?: boolean;
  claimTeacherId?: null | number | string;
}

export interface ClassRowActionContext {
  /** 是否公共课认领模式 */
  claimMode: boolean;
  /** 专属课程：能管理这门课并且有绑定班级的权限码（原来的判断） */
  canManageClasses: boolean;
  /** 有绑定班级的权限码（认领、放弃、封存、恢复都按它校验） */
  hasBindPermission: boolean;
  /** 能清除别人认领的人：管理员、公共课审批人（只在行上没有 canRelease 时用来推断） */
  canClearOthersClaim: boolean;
  /** 当前用户编号；读不到时为 0 */
  currentUserId: number;
}

export interface ClassRowActions {
  archive: boolean;
  restore: boolean;
  /** 放弃自己的认领 */
  release: boolean;
  /** 清除别人的认领（管理员、审批人） */
  clearClaim: boolean;
}

/**
 * 班级行上显示哪些修改类操作。
 * - 专属课程：封存、恢复按原来的判断，没有认领相关操作；
 * - 公共课：封存、恢复只对 canOperate=true 的行显示；
 *   放弃、清除认领以行上的 canRelease 为准：认领人是自己显示「放弃认领」，否则显示「清除认领」；
 *   旧版后端没有 canRelease 时，自己认领的行可以放弃认领，别人认领的行，管理员（canOperate=true）或审批人可以清除认领。
 */
export function getClassRowActions(
  row: ClaimClassRow,
  context: ClassRowActionContext,
): ClassRowActions {
  const archived = row.archived === true;
  if (!context.claimMode) {
    return {
      archive: context.canManageClasses && !archived,
      restore: context.canManageClasses && archived,
      release: false,
      clearClaim: false,
    };
  }
  const operable = context.hasBindPermission && row.canOperate === true;
  const claimTeacherId = Number(row.claimTeacherId ?? 0) || 0;
  const isMine =
    claimTeacherId > 0 &&
    context.currentUserId > 0 &&
    claimTeacherId === context.currentUserId;
  const isOthers = claimTeacherId > 0 && !isMine;
  if (typeof row.canRelease === 'boolean') {
    // 后端已判断认领人本人、管理员、审批人和绑定班级的权限码，不再按角色推断
    const releasable = row.canRelease && !archived;
    return {
      archive: operable && !archived,
      restore: operable && archived,
      release: releasable && isMine,
      clearClaim: releasable && !isMine,
    };
  }
  return {
    archive: operable && !archived,
    restore: operable && archived,
    release: operable && isMine && !archived,
    clearClaim:
      context.hasBindPermission &&
      isOthers &&
      !archived &&
      (row.canOperate === true || context.canClearOthersClaim),
  };
}

/**
 * 公共课看全部时，当前用户能否清除别人的认领（管理员、审批人能，课程负责人不能）。
 * 先看班级行：别人认领的行上有 canRelease 时以它为准（有一行为 true 就能）；
 * 没有别人认领的行、或旧版后端没有 canRelease 时，按角色判断（管理员或有公共课审批权限）。
 */
export function resolveCanClearOthersClaim(options: {
  currentUserId: number;
  hasAuditPermission: boolean;
  isAdmin: boolean;
  rows: Array<Pick<ClaimClassRow, 'canRelease' | 'claimTeacherId'>>;
}): boolean {
  const othersRows = options.rows.filter((row) => {
    const claimTeacherId = Number(row.claimTeacherId ?? 0) || 0;
    return claimTeacherId > 0 && claimTeacherId !== options.currentUserId;
  });
  const flags = othersRows
    .map((row) => row.canRelease)
    .filter((flag): flag is boolean => typeof flag === 'boolean');
  if (flags.length > 0) {
    return flags.includes(true);
  }
  return options.isAdmin || options.hasAuditPermission;
}

/** 课程负责人看全部时的提示：别人认领的班级只能查看 */
export const CLAIM_VIEW_NOTICE_OWNER =
  '你在查看全部班级，其他老师认领的班级只能查看';
/** 管理员、审批人看全部时的提示：可以清除教师的认领 */
export const CLAIM_VIEW_NOTICE_CLEARER =
  '你在查看全部班级，可以清除教师的认领（例如教师离职）';

/** 公共课看全部时，页面顶部的轻提示；不是看全部时不提示 */
export function getClaimViewNotice(options: {
  canClearOthersClaim: boolean;
  claimMode: boolean;
  viewAllReadOnly?: boolean;
}): string {
  if (!options.claimMode || options.viewAllReadOnly !== true) {
    return '';
  }
  return options.canClearOthersClaim
    ? CLAIM_VIEW_NOTICE_CLEARER
    : CLAIM_VIEW_NOTICE_OWNER;
}

/**
 * 只采用最后一次请求的结果：先发的请求后返回时丢弃，免得覆盖后发请求的结果
 * （如认领弹窗里快速切换学期）。
 */
export function createLatestRequestGuard() {
  let latest = 0;
  return {
    /** 发起新请求，返回它的序号 */
    next(): number {
      latest += 1;
      return latest;
    },
    /** 这个序号是否仍是最后一次请求 */
    isLatest(seq: number): boolean {
      return seq === latest;
    },
  };
}

export interface CourseMetricCopy {
  classLabel: string;
  classMeta: string;
  runningLabel: string;
  runningMeta: string;
  taskLabel: string;
  taskMeta: string;
}

/** 课程详情指标卡的文案：公共课普通教师只看自己的数据，改为「我认领的班级」等；看全部时保持原文案 */
export function getCourseMetricCopy(options: {
  claimMode: boolean;
  viewAllReadOnly?: boolean;
}): CourseMetricCopy {
  if (options.claimMode && options.viewAllReadOnly !== true) {
    return {
      classLabel: '我认领的班级',
      classMeta: '你在本课程认领的班级数',
      runningLabel: '进行中班级',
      runningMeta: '你认领的班级里正在进行的',
      taskLabel: '我布置的任务',
      taskMeta: '你在本课程布置的任务数',
    };
  }
  return {
    classLabel: '关联班级',
    classMeta: '已绑定本课程的班级总数',
    runningLabel: '进行中班级',
    runningMeta: '默认展示当前学期正在进行的班级',
    taskLabel: '实训任务',
    taskMeta: '课程下已发布的任务总数',
  };
}

/** 公共课里还没有可布置任务的班级时，提示去认领 */
export const CLAIM_CLASS_FIRST_HINT = '请先在课程详情里认领班级';

/** 不能认领时，按原因给出的引导 */
function getClaimTip(canClaim: boolean, courseOffline?: boolean) {
  if (canClaim) {
    return '点「认领班级」认领你授课的班级';
  }
  return courseOffline
    ? '课程上线后才能认领班级'
    : '请联系管理员为你开通认领班级的权限';
}

/** 公共课里没有（自己认领的）班级时的文案，字段与专属课程的 getClassAbsenceCopy 相同 */
export function getClaimClassAbsenceCopy(
  canClaim: boolean,
  courseOffline?: boolean,
) {
  const claimTip = getClaimTip(canClaim, courseOffline);
  return {
    classEmpty: `你还没有认领这门课的班级。${claimTip}，认领后才能给班级发布实训任务。`,
    taskBlocked: `实训任务只能发布到你认领的班级。${CLAIM_CLASS_FIRST_HINT}。`,
    createTaskHint: `你还没有可以布置任务的班级，不能新建任务。${CLAIM_CLASS_FIRST_HINT}。`,
    createAiTaskHint: `你还没有可以布置任务的班级，不能使用 AI 新建任务。${CLAIM_CLASS_FIRST_HINT}。`,
  };
}
