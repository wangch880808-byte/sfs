import type { StatusMeta } from './status';

import { HOMEWORK_RECORD_STATUS } from './status';

/**
 * 学生端实训任务的结果与等级状态（V1.0.1.6 界面规范）：成绩反馈页的达标情况、课程详情里的等级解锁。
 * 提交记录本身的状态用 utils/status.ts 的 getHomeworkRecordStatusMeta(status, 'student')。
 */

/**
 * 提交记录状态（edu_homework_record.status）。与 utils/status-grading.ts 的判断一样按数字严格比较，
 * 后端可能给字符串时由调用方先 Number(...)。
 */
type RecordStatus = null | number | undefined;

/** 等学生提交：未提交、已打回（能不能交还要看截止时间、等级解锁与 canSubmit） */
export function isAwaitingSubmissionStatus(status: RecordStatus): boolean {
  return (
    status === HOMEWORK_RECORD_STATUS.NOT_SUBMITTED ||
    status === HOMEWORK_RECORD_STATUS.REDO
  );
}

/**
 * 提交过：待批改、已批改、已打回、已重新提交（即 1–4）。
 * 状态只会是 0–4，所以等价于 status >= 1；null、undefined、NaN 与 0–4 以外的值都为 false。
 */
export function isSubmittedStatus(status: RecordStatus): boolean {
  return (
    status === HOMEWORK_RECORD_STATUS.SUBMITTED ||
    status === HOMEWORK_RECORD_STATUS.CORRECTED ||
    status === HOMEWORK_RECORD_STATUS.REDO ||
    status === HOMEWORK_RECORD_STATUS.RESUBMITTED
  );
}

/**
 * 交上去了、没被打回：待批改、已重新提交、已批改。学生不能再提交（与教师端
 * isCorrectableStatus 是同一组值）。
 */
export function isAlreadySubmittedStatus(status: RecordStatus): boolean {
  return (
    status === HOMEWORK_RECORD_STATUS.SUBMITTED ||
    status === HOMEWORK_RECORD_STATUS.CORRECTED ||
    status === HOMEWORK_RECORD_STATUS.RESUBMITTED
  );
}

export interface HomeworkResultInput {
  /** 提交状态：0 未提交、1 待批改、2 已批改、3 已打回、4 已重新提交 */
  status?: null | number | string;
  /** 成绩；已打回时是原成绩 */
  myScore?: null | number | string;
  /** 及格线 */
  passScore?: null | number | string;
  /** 已打回时还能不能重新提交（没过重做截止时间） */
  canResubmit?: boolean;
}

/** 批改结果的达标情况：已批改按及格线判断；已打回看还能不能重新提交；其余等教师批改 */
export function getHomeworkResultMeta(input: HomeworkResultInput): StatusMeta {
  const status = Number(input.status);
  if (status === HOMEWORK_RECORD_STATUS.CORRECTED) {
    const hasScore =
      input.myScore !== null &&
      input.myScore !== undefined &&
      input.myScore !== '';
    return hasScore && Number(input.myScore) >= Number(input.passScore)
      ? { label: '已达标', tone: 'done' }
      : { label: '未达标', tone: 'failed' };
  }
  if (status === HOMEWORK_RECORD_STATUS.REDO) {
    return input.canResubmit
      ? { label: '待重新提交', tone: 'waiting' }
      : { label: '已过重做截止时间', tone: 'failed' };
  }
  return { label: '待教师批改', tone: 'running' };
}

/** 等级闯关：已解锁的等级可以做任务（进行中），未解锁的等级要等前面的等级达标 */
export function getLevelUnlockMeta(unlocked?: boolean | null): StatusMeta {
  return unlocked
    ? { label: '已解锁', tone: 'running' }
    : { label: '未解锁', tone: 'neutral' };
}
