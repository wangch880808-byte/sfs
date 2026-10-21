import { getHomeworkRecordStatusMeta } from './status';

/** 成绩轨迹里的事件文字；状态文字与列表一致，取自 utils/status.ts（教师视角） */
const EVENT_LABELS: Record<string, string> = {
  SUBMIT: '提交',
  RESUBMIT: '重新提交',
  CORRECT: '批改',
  REDO: '打回重做',
  REDO_DEADLINE: '调整重做截止时间',
};

export function getHomeworkRecordStatusLabel(status?: null | number) {
  return getHomeworkRecordStatusMeta(status).label;
}

/** 不认识的事件写“其他记录”，不把事件类型原值显示出来 */
export function getHomeworkHistoryEventLabel(eventType: string) {
  return EVENT_LABELS[eventType] ?? '其他记录';
}

/** 打回时保留的分数是上一次成绩，不代表当前记录仍处于已批改状态。 */
export function getHomeworkHistoryScoreLabel(eventType: string) {
  return eventType === 'REDO' ||
    eventType === 'REDO_DEADLINE' ||
    eventType === 'RESUBMIT'
    ? '原成绩'
    : '成绩';
}

export function getHomeworkHistoryCommentLabel(eventType: string) {
  return eventType === 'REDO' ||
    eventType === 'REDO_DEADLINE' ||
    eventType === 'RESUBMIT'
    ? '原评语'
    : '评语';
}
