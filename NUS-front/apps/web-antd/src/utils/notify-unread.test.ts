import { describe, expect, it, vi } from 'vitest';

import {
  emitNotifyUnreadChanged,
  NOTIFY_UNREAD_CHANGED_EVENT,
} from './notify-unread';

describe('通知未读数刷新事件', () => {
  it('打开留言后发出事件，铃铛监听到后刷新', () => {
    const listener = vi.fn();
    window.addEventListener(NOTIFY_UNREAD_CHANGED_EVENT, listener);
    emitNotifyUnreadChanged();
    emitNotifyUnreadChanged();
    window.removeEventListener(NOTIFY_UNREAD_CHANGED_EVENT, listener);
    emitNotifyUnreadChanged();
    expect(listener).toHaveBeenCalledTimes(2);
  });
});
