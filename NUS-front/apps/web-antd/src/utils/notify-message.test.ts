import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

import { describe, expect, it } from 'vitest';

import { getNotifyTargetUrl } from './notify-message';

function createNotifyMessage(
  targetUrl: string,
): SystemNotifyMessageApi.NotifyMessage {
  return {
    id: 1,
    userId: 1,
    userType: 2,
    templateId: 1,
    templateCode: 'edu.admin.public-course.pending',
    templateNickname: '师砺平台',
    templateContent: '公共课待审批',
    templateType: 1,
    templateParams: { targetUrl },
    readStatus: false,
    readTime: new Date(0),
    createTime: new Date(0),
  };
}

describe('notify message target', () => {
  it('maps the legacy course URL to the current menu route', () => {
    expect(getNotifyTargetUrl(createNotifyMessage('/capacity/course'))).toBe(
      '/capacity/course_manager/course',
    );
  });

  it('keeps current business URLs unchanged', () => {
    expect(
      getNotifyTargetUrl(createNotifyMessage('/capacity/course/detail/10')),
    ).toBe('/capacity/course/detail/10');
  });
});
