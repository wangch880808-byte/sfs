import {
  CLAIM_CLASS_FIRST_HINT,
  isClassOperable,
} from '#/views/capacity/course/class-claim';

/**
 * 新建、修改任务时「指定班级」的选项（V1.0.1.10）。
 * 公共课里只能给自己认领的班级布置任务：后端在班课行上返回 canOperate，为 false 的不能选；
 * 修改已有任务时，任务原来的班级即使已不能操作、或已不在列表里（公共课放弃认领后）也保留在选项里，
 * 显示班级名称而不是编号，班级不变时可以直接保存。
 * 旧版后端没有 canOperate，全部可选。
 */
export interface CourseClassRow {
  canOperate?: boolean;
  classId: number;
  className?: string;
}

export interface ClassSelectOption {
  label: string;
  value: number;
}

/** 修改任务时要保留的原班级 */
export interface KeepClassOption {
  classId?: number;
  /** 任务列表、课程详情里带的班级名称；列表里没有这个班级时用它显示 */
  className?: string;
  /** 是否公共课：公共课里原班级已不能操作时标「已放弃认领」 */
  publicCourse?: boolean;
}

/** 已放弃认领的班级在下拉里的标注 */
export const RELEASED_CLASS_SUFFIX = '（已放弃认领）';

/** 任务列表里没有班级名称时显示「-」，不当作名称 */
function normalizeClassName(name?: string) {
  const trimmed = name?.trim();
  return trimmed && trimmed !== '-' ? trimmed : '';
}

export function buildCourseClassOptions(
  list: CourseClassRow[],
  keep?: KeepClassOption,
): ClassSelectOption[] {
  const keepId = Number(keep?.classId ?? 0) || 0;
  const releasedSuffix = keep?.publicCourse ? RELEASED_CLASS_SUFFIX : '';
  const options = list
    .filter(
      (item) =>
        isClassOperable(item) ||
        (keepId > 0 && Number(item.classId) === keepId),
    )
    .map((item) => ({
      label: `${item.className ?? `班级 ${item.classId}`}${
        isClassOperable(item) ? '' : releasedSuffix
      }`,
      value: Number(item.classId),
    }));
  if (keepId > 0 && !options.some((item) => item.value === keepId)) {
    // 公共课放弃认领后，list-by-course 不再返回这个班级：用任务带的班级名称补一个选项
    const name = normalizeClassName(keep?.className) || `班级 ${keepId}`;
    options.push({ label: `${name}${releasedSuffix}`, value: keepId });
  }
  return options;
}

/** 选了课程但没有可选班级时的占位文字 */
export const NO_OPERABLE_CLASS_PLACEHOLDER =
  '没有可选的班级：请先在课程详情里添加班级，公共课请先在课程详情里认领班级';

/** 从课程详情新建任务、课程信息不全或没有班级时的原提示 */
export const QUICK_CREATE_MISSING_HINT =
  '缺少课程信息，无法新建任务。返回课程详情后重试。';

/**
 * 从课程详情新建任务时不能打开表单的提示；能打开时为空。
 * 公共课（课程详情传入 claimMode=true）里没有自己认领的班级时提示先认领；专属课程保持原提示。
 */
export function resolveQuickCreateBlockHint(options: {
  claimMode?: boolean;
  fixedCourseId?: number;
  fixedIndicatorFirstId?: number;
  relationCount: number;
}): string {
  if (
    options.fixedCourseId &&
    options.fixedIndicatorFirstId &&
    options.relationCount > 0
  ) {
    return '';
  }
  if (
    options.claimMode === true &&
    options.fixedCourseId &&
    options.fixedIndicatorFirstId
  ) {
    return `没有可以布置任务的班级。${CLAIM_CLASS_FIRST_HINT}。`;
  }
  return QUICK_CREATE_MISSING_HINT;
}
