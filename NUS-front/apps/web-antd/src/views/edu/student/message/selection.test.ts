import { describe, expect, it } from 'vitest';

import { findConversation } from './selection';

const conversations = [
  { courseId: 2, classId: 1 },
  { courseId: 2, classId: 3 },
  { courseId: 4, classId: 1 },
] as Parameters<typeof findConversation>[0];

describe('findConversation', () => {
  it('matches course and class from a notification', () => {
    expect(findConversation(conversations, 2, 3)?.classId).toBe(3);
  });

  it('opens the course conversation from a course detail link', () => {
    expect(findConversation(conversations, 4)?.courseId).toBe(4);
  });

  it('does not choose another class when the requested one is absent', () => {
    expect(findConversation(conversations, 2, 9)).toBeUndefined();
  });
});
