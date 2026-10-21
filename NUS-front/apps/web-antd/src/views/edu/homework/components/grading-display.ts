import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';
import type { DeadlineValue } from '#/utils/homework-deadline';

import dayjs from 'dayjs';

import { formatDeadlineTime, toDeadlineTime } from '#/utils/homework-deadline';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { isPreviousScoreStatus, isRedoStatus } from '#/utils/status-grading';

/**
 * 批改工作台的显示文字（V1.0.1.6 界面规范）：只做呈现，不改记录状态与批改规则。
 */

/** 列表里的短时间“月-日 时:分”；解析不了时返回空串 */
export function formatShortTime(value: DeadlineValue): string {
  const time = toDeadlineTime(value);
  return time === undefined ? '' : dayjs(time).format('MM-DD HH:mm');
}

/** 列表行只需要这几个字段；批改队列的行（QueueItem）与完整记录都可以传 */
interface RecordListFields {
  redoDeadline?: DeadlineValue;
  status?: EduHomeworkRecordApi.HomeworkRecord['status'];
  studentNo?: string;
  submitTime?: DeadlineValue;
}

/** 列表里的短日期“月-日”；解析不了时返回空串 */
export function formatShortDate(value: DeadlineValue): string {
  const time = toDeadlineTime(value);
  return time === undefined ? '' : dayjs(time).format('MM-DD');
}

/** 学生记录列表的第二行：学号，加提交时间；已打回的记录改写重做截止日期（完整时间在右栏） */
export function getRecordListMeta(record: RecordListFields): string {
  const deadline = isRedoStatus(record.status)
    ? formatShortDate(record.redoDeadline)
    : '';
  const time = deadline
    ? `重做截止 ${deadline}`
    : formatShortTime(record.submitTime);
  return [record.studentNo, time].filter(Boolean).join(' · ');
}

/** 已打回、已重新提交的记录，分数是上一次的成绩 */
export function getScoreLabel(status?: number): string {
  return isPreviousScoreStatus(status) ? '原成绩' : '得分';
}

/** 批改表单不可用时的原因；能批改（待批改、已批改、已重新提交）时返回空串 */
export function getCorrectBlockedReason(status?: number): string {
  switch (status) {
    case HOMEWORK_RECORD_STATUS.CORRECTED:
    case HOMEWORK_RECORD_STATUS.RESUBMITTED:
    case HOMEWORK_RECORD_STATUS.SUBMITTED: {
      return '';
    }
    case HOMEWORK_RECORD_STATUS.NOT_SUBMITTED: {
      return '学生还没有提交，提交后才能批改。';
    }
    case HOMEWORK_RECORD_STATUS.REDO: {
      return '已打回重做，学生重新提交后才能批改。';
    }
    default: {
      return '这条记录现在不能批改。';
    }
  }
}

interface GradingSummary {
  corrected: number;
  deadline?: DeadlineValue;
  pending: number;
  total: number;
}

/** 页头说明：记录数、已批改、待批改与任务截止时间，一行写完 */
export function getGradingSummary(summary: GradingSummary): string {
  const parts = [
    `共 ${summary.total} 条记录`,
    `已批改 ${summary.corrected}`,
    `待批改 ${summary.pending}`,
  ];
  const deadline = formatDeadlineTime(summary.deadline, '');
  if (deadline) {
    parts.push(`截止 ${deadline}`);
  }
  return parts.join(' · ');
}
