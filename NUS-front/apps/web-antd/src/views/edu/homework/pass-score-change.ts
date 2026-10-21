import { HOMEWORK_STATUS } from '#/utils/status';

/**
 * 编辑已发布任务时修改达标分数线的提示（保存前的预览即确认框里显示）。
 * 改分数线允许，但已批改的成绩会按新分数线重新判断达标，后端同时记入操作日志。
 */

export interface PassScoreChange {
  from: number;
  to: number;
  message: string;
}

interface HomeworkLike {
  id?: number;
  passScore?: null | number | string;
  status?: null | number;
}

/** 已发布（含已截止）的任务改了达标分数线时给出提示；新建、待发布（旧草稿）或没改时为空 */
export function resolvePassScoreChange(
  original: HomeworkLike | undefined,
  nextPassScore: null | number | string | undefined,
): null | PassScoreChange {
  if (
    !original?.id ||
    Number(original.status ?? HOMEWORK_STATUS.DRAFT) === HOMEWORK_STATUS.DRAFT
  ) {
    return null;
  }
  const from = Number(original.passScore);
  const to = Number(nextPassScore);
  if (!Number.isFinite(from) || !Number.isFinite(to) || from === to) {
    return null;
  }
  return {
    from,
    to,
    message: `达标分数线从 ${from} 分改为 ${to} 分：已批改的成绩会按新分数线重新判断是否达标，达标人数、能力分析和等级闯关进度随之变化；学生已解锁的等级不会收回。这次修改会记入操作日志。`,
  };
}
