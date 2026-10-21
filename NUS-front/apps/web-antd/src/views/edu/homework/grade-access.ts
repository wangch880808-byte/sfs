/**
 * 任务的批改权限：管理员、课程负责人、课程团队成员能查看别人布置的任务及其提交与成绩，
 * 但批改、打回、撤销、AI 初评、个别延期、催交、修改、封存与删除只有布置者能做。
 * 后端在任务详情里返回 canGrade；旧版后端没有这个字段时按能批改处理，保持原来的样子。
 */

/** 不是布置者时页面上方的说明 */
export const HOMEWORK_VIEW_ONLY_NOTICE =
  '你不是这个任务的布置者，只能查看提交和成绩，批改由布置者完成。';

/** 只读时批改表单里的说明 */
export const HOMEWORK_VIEW_ONLY_REASON = '只有任务布置者可以批改这份提交。';

/** 只读：后端明确说当前用户不能批改（canGrade 为 false） */
export function isHomeworkViewOnly(homework?: { canGrade?: boolean }): boolean {
  return homework?.canGrade === false;
}
