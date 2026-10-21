import type { DeadlineValue } from '#/utils/homework-deadline';

import { isHomeworkArchived } from '#/utils/status';

import {
  canExtendHomework,
  canRemindHomework,
} from './components/extension-display';
import { HOMEWORK_VIEW_ONLY_NOTICE, isHomeworkViewOnly } from './grade-access';

/**
 * 任务详情页页头操作：统计接口说只读（任务已封存，或它所在的班级、课程、班课已封存）时，
 * 只留查看，不再显示进入批改、个别延期与催交；后端也会拒绝这些改动。
 * 当前用户不是布置者（canGrade 为 false）时同样不显示批改、延期与催交，改为“查看提交”进入只读的批改页。
 */

/** 只读时页面上方的说明 */
export const HOMEWORK_READ_ONLY_NOTICE =
  '这个任务或它所在的班级、课程已封存，成绩只能查看，不能再批改、打回或修改。';

export interface HomeworkDetailActions {
  /** 只读：只能查看 */
  readOnly: boolean;
  /** 不是布置者：只能查看提交和成绩 */
  viewOnly: boolean;
  canCorrect: boolean;
  canExtend: boolean;
  canRemind: boolean;
  /** 不是布置者时进入批改页只读查看提交 */
  canViewSubmissions: boolean;
  /** 页面上方的说明；没有时为空串 */
  notice: string;
}

export function resolveHomeworkDetailActions(
  homework?: { canGrade?: boolean; deadline?: DeadlineValue; status?: number },
  statistics?: { readOnly?: boolean },
): HomeworkDetailActions {
  const readOnly = statistics?.readOnly === true;
  const viewOnly = isHomeworkViewOnly(homework);
  const visible = !!homework && !readOnly;
  const editable = visible && !viewOnly;
  const notArchived = !isHomeworkArchived(homework?.status);
  let notice = '';
  if (readOnly) notice = HOMEWORK_READ_ONLY_NOTICE;
  else if (viewOnly) notice = HOMEWORK_VIEW_ONLY_NOTICE;
  return {
    readOnly,
    viewOnly,
    canCorrect: editable && notArchived,
    canExtend: editable && canExtendHomework(homework),
    canRemind: editable && canRemindHomework(homework),
    canViewSubmissions: visible && viewOnly && notArchived,
    notice,
  };
}
