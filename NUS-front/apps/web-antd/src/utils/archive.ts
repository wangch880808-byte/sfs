/** 封存操作统一文案 */
export const ARCHIVE_COPY = {
  showArchived: '显示已封存',
  archived: '已封存',
  archive: '封存',
  archiveBatch: '批量封存',
  restore: '恢复',
  restoreBatch: '批量恢复',
  archiveHint:
    '封存后只读，学生的待办里不再出现，历史数据仍计入统计，可随时恢复',
  archivingBatch: '正在批量封存…',
  archivedBatch: '批量封存成功',
  restoredBatch: '批量恢复成功',
} as const;

export const ACCOUNT_COPY = {
  showDisabled: '显示已停用',
  disabled: '已停用',
  disable: '停用',
  disableBatch: '批量停用',
  restore: '恢复',
  restoreBatch: '批量恢复',
  disablingBatch: '正在批量停用…',
  disabledBatch: '批量停用成功',
  restoredBatch: '批量恢复成功',
} as const;

/** 行内封存的确认标题：写明对象、后果与恢复办法（确认框正文见 utils/confirm-copy.ts） */
export function archiveConfirm(name: string) {
  return `封存「${name}」？${ARCHIVE_COPY.archiveHint}。`;
}

export function archivingMessage(name: string) {
  return `正在封存「${name}」…`;
}

export function archivedMessage(name: string) {
  return `已封存「${name}」`;
}

export function restoredMessage(name: string) {
  return `已恢复「${name}」`;
}

export function isArchived(status?: null | number, archivedStatus = 1) {
  return Number(status) === archivedStatus;
}

export function disablingMessage(name: string) {
  return `正在停用「${name}」…`;
}

export function disabledMessage(name: string) {
  return `已停用「${name}」`;
}
