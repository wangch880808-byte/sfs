import type { EduHomeworkApi } from '#/api/edu/homework';

/**
 * 删除实训任务的确认框文案（V1.0.1.10）。删除连同提交、成绩一起删掉，不能恢复，
 * 所以先查影响范围（getHomeworkRemoveImpact），再按这里的文案弹危险确认框。
 * 任务列表与课程详情共用；确认按钮写「删除」，另一个按钮写「返回」。
 */

export interface RemoveConfirmCopy {
  content: string;
  danger: true;
  title: string;
}

/** 确认按钮 */
export const REMOVE_OK_TEXT = '删除';
/** 另一个按钮 */
export const REMOVE_CANCEL_TEXT = '返回';

function toCount(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
}

/**
 * 标题：只删一个任务且知道名称时写名称，否则写个数。
 * 正文：有提交时写明要删掉的提交与成绩份数、统计不再计入、不能恢复，并给出封存这个可恢复的办法；
 * 没有提交时简短确认。
 */
export function buildRemoveConfirm(
  impact?: null | Partial<EduHomeworkApi.HomeworkRemoveImpact>,
  name?: null | string,
): RemoveConfirmCopy {
  const homeworkCount = Math.max(toCount(impact?.homeworkCount), 1);
  const submittedCount = toCount(impact?.submittedCount);
  const trimmedName = name?.trim();
  let title: string;
  if (homeworkCount > 1) {
    title = `删除选中的 ${homeworkCount} 个实训任务？`;
  } else if (trimmedName) {
    title = `删除实训任务「${trimmedName}」？`;
  } else {
    title = '删除这个实训任务？';
  }
  const content =
    submittedCount > 0
      ? `将删除 ${submittedCount} 份提交和成绩，所有统计不再计入，且不能恢复。只想让学生看不到的话，改用「封存」。`
      : '还没有学生提交。删除后不能恢复。';
  return { content, danger: true, title };
}
