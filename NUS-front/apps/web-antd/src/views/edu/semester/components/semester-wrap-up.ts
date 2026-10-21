/**
 * 学期收尾对话框的步骤、默认选择与文案（纯函数，便于单测）。
 */

export const WRAP_UP_STEPS = ['预览', '选择', '确认', '结果'] as const;

export const WRAP_UP_ARCHIVE_EFFECT =
  '封存后只读，不能再布置或修改任务；学生的待办里不再出现，教师的班级与任务列表默认也不再显示。已有的提交、成绩和评语都保留，仍计入合格率、成绩趋势等统计。';

export const WRAP_UP_RESTORE_HINT =
  '需要时可以恢复：班级课程在实训课程详情的「班级」里打开「显示已封存」后恢复；班级在「班级管理」里打开「显示已封存」后恢复。';

export const WRAP_UP_OPEN_WORK_HINT =
  '还有进行中的任务或等待批改的提交。封存后只读，学生不能再提交，教师也不能再批改。建议先批改完、等任务截止后再封存。';

export const WRAP_UP_LEAVE_BLOCKED = '正在封存，完成后再离开这个页面。';

interface SemesterLike {
  activeClassCourseCount?: number;
  current?: boolean;
  semesterName: string;
}

interface ClassCourseLike {
  classCourseId: number;
  openTaskCount?: number;
  pendingReviewCount?: number;
}

/** 默认收尾的学期：最近一个仍有在用班级课程的往期学期；没有时取当前学期 */
export function defaultWrapUpSemester(options: SemesterLike[]) {
  const previous = options.find(
    (item) => !item.current && Number(item.activeClassCourseCount || 0) > 0,
  );
  return (previous ?? options.find((item) => item.current) ?? options[0])
    ?.semesterName;
}

/** 默认勾选：没有进行中任务、也没有待批改提交的班级课程 */
export function defaultWrapUpSelection(classCourses: ClassCourseLike[]) {
  return classCourses
    .filter(
      (item) =>
        Number(item.openTaskCount || 0) === 0 &&
        Number(item.pendingReviewCount || 0) === 0,
    )
    .map((item) => item.classCourseId);
}

/** 所选范围里还有进行中的任务或待批改的提交 */
export function hasOpenWork(
  classCourses: ClassCourseLike[],
  selectedIds: number[],
) {
  const selected = new Set(selectedIds);
  return classCourses.some(
    (item) =>
      selected.has(item.classCourseId) &&
      (Number(item.openTaskCount || 0) > 0 ||
        Number(item.pendingReviewCount || 0) > 0),
  );
}

/** 确认标题：“封存「2025-2026 下学期」的 12 个班级课程和 2 个班级？” */
export function wrapUpConfirmTitle(
  semesterName: string,
  classCourseCount: number,
  classCount: number,
) {
  const parts = [];
  if (classCourseCount > 0) parts.push(`${classCourseCount} 个班级课程`);
  if (classCount > 0) parts.push(`${classCount} 个班级`);
  return `封存「${semesterName}」的 ${parts.join('和 ')}？`;
}

/** 结果摘要 */
export function wrapUpResultText(
  semesterName: string,
  classCourseCount: number,
  classCount: number,
) {
  const parts = [`${classCourseCount} 个班级课程`];
  if (classCount > 0) parts.push(`${classCount} 个班级`);
  return `「${semesterName}」已收尾：封存了 ${parts.join('和 ')}。`;
}

/**
 * 离开学期页（切换路由、页签失活）时弹窗怎么处理：
 * 正在封存时不让离开，弹窗保持打开；其余情况都关闭弹窗，免得它浮在别的页面上。
 */
export function resolveWrapUpLeave(state: {
  open: boolean;
  submitting: boolean;
}) {
  return state.open && state.submitting
    ? { allowLeave: false, open: true }
    : { allowLeave: true, open: false };
}
