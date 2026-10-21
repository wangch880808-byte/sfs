/**
 * 页面把通知标为已读之后（例如打开留言时后端顺带把这串留言的通知标为已读），
 * 用这个事件让顶栏铃铛立即重新读取未读数，不必等下一次轮询。
 */
export const NOTIFY_UNREAD_CHANGED_EVENT = 'edu-notify-unread-changed';

export function emitNotifyUnreadChanged() {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new Event(NOTIFY_UNREAD_CHANGED_EVENT));
}
