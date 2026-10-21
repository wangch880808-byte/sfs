import { describe, expect, it } from 'vitest';

import {
  CONVERSATION_STATUS,
  getConversationStatusMeta,
  isConversationClosed,
} from './status-message';

describe('留言会话状态', () => {
  it('状态值 1 为已关闭，显示为中性', () => {
    expect(getConversationStatusMeta(CONVERSATION_STATUS.CLOSED)).toEqual({
      label: '已关闭',
      tone: 'neutral',
    });
    expect(getConversationStatusMeta('1')).toEqual({
      label: '已关闭',
      tone: 'neutral',
    });
    expect(isConversationClosed(1)).toBe(true);
  });

  it('其余取值（含还没读到详情）为进行中', () => {
    for (const status of [CONVERSATION_STATUS.OPEN, null, undefined, '']) {
      expect(getConversationStatusMeta(status)).toEqual({
        label: '进行中',
        tone: 'running',
      });
      expect(isConversationClosed(status)).toBe(false);
    }
  });
});
