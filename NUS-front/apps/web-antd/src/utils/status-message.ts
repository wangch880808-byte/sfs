import type { StatusMeta } from './status';

/**
 * 留言会话的状态呈现（V1.0.1.6 界面规范）：教师“留言互动”与学生“我的留言”共用。
 * 会话状态值 1 为已关闭（不能继续留言），其余为进行中；类别沿用 utils/status.ts：进行中 running、已关闭 neutral。
 */
export const CONVERSATION_STATUS = {
  OPEN: 0,
  CLOSED: 1,
} as const;

export function isConversationClosed(
  status: null | number | string | undefined,
): boolean {
  return Number(status) === CONVERSATION_STATUS.CLOSED;
}

export function getConversationStatusMeta(
  status: null | number | string | undefined,
): StatusMeta {
  return isConversationClosed(status)
    ? { label: '已关闭', tone: 'neutral' }
    : { label: '进行中', tone: 'running' };
}
