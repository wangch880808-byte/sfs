import type { DeadlineValue } from '#/utils/homework-deadline';

import { isRedoDeadlineEffective } from '#/utils/homework-deadline';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';

/**
 * 学生任务列表的截止时间标注：已打回时标「重做」，教师单独延期且还没提交时标「延期」。
 * 截止时间本身用后端算好的实际截止时间（resolveEffectiveDeadline）。
 */
export interface PersonalDeadlineFields {
  personalDeadline?: DeadlineValue;
  redoDeadline?: DeadlineValue;
  status?: number;
}

function isBlank(value: DeadlineValue) {
  return value === undefined || value === null || value === '';
}

/** 截止时间旁的标注文字；没有标注时返回空字符串 */
export function getDeadlineBadge(item: PersonalDeadlineFields) {
  if (isRedoDeadlineEffective(item)) {
    return '重做';
  }
  if (
    Number(item.status ?? HOMEWORK_RECORD_STATUS.NOT_SUBMITTED) ===
      HOMEWORK_RECORD_STATUS.NOT_SUBMITTED &&
    !isBlank(item.personalDeadline)
  ) {
    return '延期';
  }
  return '';
}
