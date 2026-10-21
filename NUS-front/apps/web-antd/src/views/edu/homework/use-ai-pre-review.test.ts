import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getAiDraftCommentLabel,
  getAiDraftCommentText,
  getAiDraftNote,
  useAiPreReview,
} from './use-ai-pre-review';

const api = vi.hoisted(() => ({
  get: vi.fn(),
  reject: vi.fn(),
  trigger: vi.fn(),
}));

vi.mock('#/api/edu/homework-ai-review', () => ({
  getHomeworkAiReviewByRecord: api.get,
  rejectHomeworkAiReview: api.reject,
  triggerHomeworkAiReview: api.trigger,
}));

function review(status: number, extra: Record<string, unknown> = {}) {
  return { homeworkRecordId: 1, id: 9, status, ...extra };
}

describe('aI 初评区块的文字', () => {
  it('排队中与处理中分开写', () => {
    expect(getAiDraftNote(undefined, true)).toBe('正在读取 AI 初评…');
    expect(getAiDraftNote(undefined, false)).toBe('还没有 AI 初评。');
    expect(getAiDraftNote(review(0), false)).toBe('排队中…');
    expect(getAiDraftNote(review(1), false)).toBe('AI 正在初评…');
  });

  it('跳过写跳过原因，失败写保存的失败原因', () => {
    expect(getAiDraftCommentLabel(6)).toBe('跳过原因');
    expect(getAiDraftCommentLabel(3)).toBe('失败原因');
    expect(getAiDraftCommentLabel(2)).toBe('初评评语');
    expect(
      getAiDraftCommentText(
        review(3, { aiComment: '旧', errorMessage: '模型超时' }),
      ),
    ).toBe('模型超时');
    expect(
      getAiDraftCommentText(review(6, { aiComment: '只有视频，需人工批改' })),
    ).toBe('只有视频，需人工批改');
  });
});

describe('useAiPreReview', () => {
  beforeEach(() => {
    api.get.mockReset();
    api.trigger.mockReset();
    api.reject.mockReset();
  });

  it('失败后按钮写“重新生成”；触发报错写在区块里，请求不弹全局提示', async () => {
    api.get.mockResolvedValue(review(3, { errorMessage: '模型超时' }));
    const ai = useAiPreReview(() => 1);
    await ai.load(1);
    expect(api.get).toHaveBeenCalledWith(1, { silentError: true });
    expect(ai.triggerLabel.value).toBe('重新生成');
    expect(ai.draftVisible.value).toBe(true);

    api.trigger.mockRejectedValueOnce({
      data: { code: 1_050_010_100, msg: '这项任务没有开启 AI 初评' },
    });
    await expect(ai.trigger()).resolves.toBe(false);
    expect(api.trigger).toHaveBeenCalledWith(1, { silentError: true });
    expect(ai.errorText.value).toBe('这项任务没有开启 AI 初评');
  });

  it('换了学生后，旧请求的结果不再写入', async () => {
    let current = 1;
    let resolveOld: (value: unknown) => void = () => {};
    api.get.mockReturnValueOnce(
      new Promise((resolve) => (resolveOld = resolve)),
    );
    const ai = useAiPreReview(() => current);
    const pending = ai.load(1);
    current = 2;
    resolveOld(review(2));
    await pending;
    expect(ai.review.value).toBeUndefined();
  });
});
