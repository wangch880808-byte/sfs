/**
 * 课程详情「班级」页签的筛选（V1.0.1.9 A1，V1.0.1.10 加负责人）。默认只显示进行中的班级；
 * 添加班级后，如果新班级在当前筛选下看不到，页面改为显示全部班级并提示。
 */

export interface ClassFilterState {
  keyword: string;
  semester: string;
  status: 'all' | '已结束' | '未开始' | '进行中';
  /** 负责人：见 getClassOwnerKey；不选（undefined）时不按负责人筛选 */
  ownerKey?: string;
}

/** 筛选只用到班级的这几个字段 */
export interface ClassFilterRow {
  archived?: boolean;
  classId: number;
  className?: string;
  semesterName?: string;
  status?: string;
  /** 负责人（V1.0.1.10）：公共课为认领人，未认领时为空 */
  ownerId?: null | number;
  ownerName?: null | string;
  /** 公共课认领人：旧版后端没有 ownerId、ownerName 时用它 */
  claimTeacherId?: null | number | string;
  claimTeacherName?: null | string;
}

/** 负责人筛选里「未认领」的取值（公共课没有认领人的班级） */
export const UNCLAIMED_OWNER_KEY = 'unclaimed';
/** 公共课没有认领人时显示的文字（与后端 ownerName 一致） */
export const UNCLAIMED_OWNER_LABEL = '未认领';

/** 默认筛选：只显示进行中的班级 */
export function defaultClassFilter(): ClassFilterState {
  return {
    keyword: '',
    semester: 'all',
    status: '进行中',
    ownerKey: undefined,
  };
}

/** 清除筛选：显示全部班级 */
export function clearedClassFilter(): ClassFilterState {
  return { keyword: '', semester: 'all', status: 'all', ownerKey: undefined };
}

function positiveId(value: null | number | string | undefined): number {
  const id = Number(value ?? 0);
  return Number.isFinite(id) && id > 0 ? id : 0;
}

/** 后端返回的负责人姓名；「未认领」「-」等占位不算姓名 */
function realOwnerName(row: ClassFilterRow): string {
  for (const value of [row.ownerName, row.claimTeacherName]) {
    const name = value?.trim() ?? '';
    if (name && name !== UNCLAIMED_OWNER_LABEL && name !== '-') {
      return name;
    }
  }
  return '';
}

/**
 * 负责人的筛选取值：有负责人编号时按编号（`id:编号`），旧版后端只有姓名时按姓名（`name:姓名`），
 * 都没有时为「未认领」。
 */
export function getClassOwnerKey(row: ClassFilterRow): string {
  const id = positiveId(row.ownerId) || positiveId(row.claimTeacherId);
  if (id > 0) {
    return `id:${id}`;
  }
  const name = realOwnerName(row);
  return name ? `name:${name}` : UNCLAIMED_OWNER_KEY;
}

/**
 * 「负责人」列显示的文字：ownerName，没有时用 claimTeacherName；
 * 都没有时公共课显示「未认领」，专属课程显示「-」。
 */
export function getClassOwnerLabel(
  row: ClassFilterRow,
  claimMode: boolean,
): string {
  const name = row.ownerName?.trim() || row.claimTeacherName?.trim() || '';
  if (name) {
    return name;
  }
  return claimMode ? UNCLAIMED_OWNER_LABEL : '-';
}

/** 「负责人」列是否用弱化样式：公共课里没有认领人的班级 */
export function isClassOwnerUnclaimed(
  row: ClassFilterRow,
  claimMode: boolean,
): boolean {
  return claimMode && getClassOwnerKey(row) === UNCLAIMED_OWNER_KEY;
}

export interface OwnerOption {
  label: string;
  value: string;
}

/**
 * 负责人下拉的选项：从班级行里去重（按负责人编号），按姓名排序；
 * 只看所选学期的班级（semester 为 all 时不限）。公共课在最后多一个「未认领」。
 */
export function buildClassOwnerOptions(
  rows: ClassFilterRow[],
  options: { claimMode: boolean; semester: string },
): OwnerOption[] {
  const map = new Map<string, string>();
  rows
    .filter(
      (item) =>
        options.semester === 'all' || item.semesterName === options.semester,
    )
    .forEach((item) => {
      const key = getClassOwnerKey(item);
      if (key === UNCLAIMED_OWNER_KEY || map.has(key)) {
        return;
      }
      map.set(key, realOwnerName(item) || '-');
    });
  const list = [...map.entries()]
    .map(([value, label]) => ({ label, value }))
    .toSorted((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
  if (options.claimMode) {
    list.push({ label: UNCLAIMED_OWNER_LABEL, value: UNCLAIMED_OWNER_KEY });
  }
  return list;
}

/** 选中的负责人已不在选项里时清掉（例如换了学期、关了显示已封存） */
export function keepOwnerKey(
  ownerKey: string | undefined,
  options: Array<{ value: string }>,
): string | undefined {
  if (!ownerKey) {
    return undefined;
  }
  return options.some((item) => item.value === ownerKey) ? ownerKey : undefined;
}

/** 按关键词、学期、状态与负责人筛选班级；按状态筛选时不含已封存的班课 */
export function filterClassRows<T extends ClassFilterRow>(
  rows: T[],
  filter: ClassFilterState,
): T[] {
  const keyword = filter.keyword.trim().toLowerCase();
  const ownerKey = filter.ownerKey || '';
  return rows.filter((item) => {
    const byKeyword = keyword
      ? (item.className ?? '').toLowerCase().includes(keyword)
      : true;
    const bySemester =
      filter.semester === 'all' ? true : item.semesterName === filter.semester;
    const byStatus =
      filter.status === 'all'
        ? true
        : !item.archived && item.status === filter.status;
    const byOwner = ownerKey ? getClassOwnerKey(item) === ownerKey : true;
    return byKeyword && bySemester && byStatus && byOwner;
  });
}

/**
 * 刚添加的班级里，是否有在当前筛选下看不到的。
 * 刚添加的班级 = 所选学期里、未封存、班级编号在本次添加之列的班课；详情里没读到的不算。
 */
export function hasHiddenAddedClasses(
  rows: ClassFilterRow[],
  filter: ClassFilterState,
  added: { classIds: number[]; semesterName: string },
): boolean {
  const idSet = new Set(added.classIds.map(Number));
  const semesterName = added.semesterName.trim();
  const addedRows = rows.filter(
    (item) =>
      !item.archived &&
      idSet.has(Number(item.classId)) &&
      (item.semesterName ?? '').trim() === semesterName,
  );
  return filterClassRows(addedRows, filter).length < addedRows.length;
}
