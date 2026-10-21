import { describe, expect, it } from 'vitest';

import {
  buildUploadAccept,
  checkAttachmentBatch,
  DEFAULT_ATTACHMENT_LIMITS,
  describeAttachmentLimits,
  getAttachmentCategory,
  resolveAttachmentLimits,
} from './attachment-rules';

const MB = 1024 * 1024;

describe('附件上限', () => {
  it('后端没返回上限时用默认值，类型按任务配置推断', () => {
    expect(resolveAttachmentLimits({ submissionMode: 'mixed' })).toEqual({
      allowedTypes: ['image', 'audio', 'video'],
      ...DEFAULT_ATTACHMENT_LIMITS,
    });
    expect(
      resolveAttachmentLimits({
        requiredAttachmentTypesJson: '["audio"]',
        submissionMode: 'mixed',
      }).allowedTypes,
    ).toEqual(['audio']);
    expect(resolveAttachmentLimits({}).allowedTypes).toEqual([]);
  });

  it('以后端返回的上限与类型为准，无效值回退默认', () => {
    expect(
      resolveAttachmentLimits({
        attachmentLimits: {
          allowedTypes: ['IMAGE'],
          maxCount: 3,
          maxFileSizeMb: 20,
          maxTotalSizeMb: 0,
        },
        submissionMode: 'mixed',
      }),
    ).toEqual({
      allowedTypes: ['image'],
      maxCount: 3,
      maxFileSizeMb: 20,
      maxTotalSizeMb: DEFAULT_ATTACHMENT_LIMITS.maxTotalSizeMb,
    });
  });

  it('提示文字写明上限，accept 用通配写法', () => {
    expect(
      describeAttachmentLimits(resolveAttachmentLimits({ submissionMode: '' })),
    ).toBe('最多 10 个附件，单个不超过 100 MB，合计不超过 300 MB。');
    expect(buildUploadAccept(['image', 'audio'])).toBe('image/*,audio/*');
    expect(buildUploadAccept([])).toBe('');
  });

  it('类型按 MIME 判断，浏览器没给类型时看扩展名', () => {
    expect(getAttachmentCategory({ name: 'a.bin', type: 'audio/mpeg' })).toBe(
      'audio',
    );
    expect(getAttachmentCategory({ name: '录音.M4A', type: '' })).toBe('audio');
    expect(getAttachmentCategory({ name: 'report.pdf' })).toBe('document');
  });
});

describe('上传前检查', () => {
  const limits = {
    allowedTypes: ['image'],
    maxCount: 3,
    maxFileSizeMb: 10,
    maxTotalSizeMb: 15,
  };

  it('类型不符、空文件、单个超限分别说明原因', () => {
    const results = checkAttachmentBatch(
      [
        { name: 'a.mp3', size: MB, type: 'audio/mpeg' },
        { name: 'b.png', size: 0, type: 'image/png' },
        { name: 'c.png', size: 10 * MB + 1, type: 'image/png' },
        { name: 'd.png', size: 10 * MB, type: 'image/png' },
      ],
      [],
      limits,
    );
    expect(results[0]).toBe('「a.mp3」没有添加：这个任务只能上传图片。');
    expect(results[1]).toContain('文件是空的');
    expect(results[2]).toContain('单个附件不能超过 10 MB');
    expect(results[3]).toBeNull();
  });

  it('个数与合计大小把列表里已有的附件和同一批前面通过的文件都算上', () => {
    const results = checkAttachmentBatch(
      [
        { name: '1.png', size: 4 * MB, type: 'image/png' },
        { name: '2.png', size: 4 * MB, type: 'image/png' },
        { name: '3.png', size: MB, type: 'image/png' },
      ],
      [{ size: 8 * MB }],
      limits,
    );
    expect(results[0]).toBeNull();
    // 已有 8 MB + 4 MB，再加 4 MB 超过合计 15 MB
    expect(results[1]).toContain('附件合计不能超过 15 MB');
    // 被拒的不占名额：已有 1 个 + 通过 1 个，第 3 个可以
    expect(results[2]).toBeNull();

    const full = checkAttachmentBatch(
      [{ name: '4.png', size: MB, type: 'image/png' }],
      [{}, {}, {}],
      limits,
    );
    expect(full[0]).toContain('最多上传 3 个附件');
  });

  it('不限类型时任何文件都能加', () => {
    expect(
      checkAttachmentBatch([{ name: '报告.docx', size: MB }], [], {
        ...limits,
        allowedTypes: [],
      }),
    ).toEqual([null]);
  });
});
