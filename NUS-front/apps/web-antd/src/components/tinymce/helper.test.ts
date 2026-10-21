import { describe, expect, it } from 'vitest';

import { toImageUploadFailure } from './helper';

describe('编辑器图片上传失败', () => {
  it('写明后端给的原因，并让编辑器拿掉没传上去的图片', () => {
    expect(
      toImageUploadFailure({ data: { code: 500, msg: '文件大小超过限制。' } }),
    ).toEqual({
      message: '文件大小超过限制。这张图片没有插入，可以重新粘贴或上传。',
      remove: true,
    });
  });

  it('断网写成中文原因；读不出原因时写原因未知', () => {
    expect(
      toImageUploadFailure({
        config: { method: 'post' },
        message: 'Network Error',
      }).message,
    ).toBe(
      '网络中断或响应超时，结果没有确认。这张图片没有插入，可以重新粘贴或上传。',
    );
    expect(toImageUploadFailure(new Error('boom')).message).toBe(
      '原因未知。这张图片没有插入，可以重新粘贴或上传。',
    );
  });
});
