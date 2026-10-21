import type { EduHomeworkApi } from '#/api/edu/homework';

import { describe, expect, it } from 'vitest';

import { createTolerantRequest, mergeRegeneratedItem } from './ai-regenerate';

describe('mergeRegeneratedItem', () => {
  it('replaces the question when regeneration succeeded', () => {
    const local: EduHomeworkApi.HomeworkAiCreatePreviewItem = {
      homeworkName: '旧标题',
      previewItemId: 'a',
      regenerating: true,
      requirement: '旧要求',
      sourceQuestionId: 1,
      success: false,
    };
    const ok = mergeRegeneratedItem(local, {
      homeworkName: '新标题',
      previewItemId: 'a',
      regenerating: false,
      requirement: '新要求',
      sourceQuestionId: 1,
      success: true,
    });
    expect(ok).toBe(true);
    expect(local).toMatchObject({
      errorMessage: undefined,
      homeworkName: '新标题',
      regenerating: false,
      requirement: '新要求',
      success: true,
    });
  });

  it('keeps local edits and shows the reason when regeneration failed', () => {
    const local: EduHomeworkApi.HomeworkAiCreatePreviewItem = {
      homeworkName: '我改过的标题',
      previewItemId: 'a',
      regenerating: true,
      requirement: '我改过的要求',
      sourceQuestionId: 1,
      success: true,
    };
    const ok = mergeRegeneratedItem(local, {
      errorMessage: '重新生成没有成功：今日 AI 用量已达上限。',
      homeworkName: '服务器上的旧标题',
      previewItemId: 'a',
      requirement: '服务器上的旧要求',
      sourceQuestionId: 1,
      success: true,
    });
    expect(ok).toBe(false);
    expect(local.homeworkName).toBe('我改过的标题');
    expect(local.requirement).toBe('我改过的要求');
    expect(local.errorMessage).toContain('今日 AI 用量已达上限');
    expect(local.regenerating).toBe(false);
  });

  it('marks an empty question as failed', () => {
    const local: EduHomeworkApi.HomeworkAiCreatePreviewItem = {
      homeworkName: '',
      previewItemId: 'a',
      sourceQuestionId: 1,
      success: true,
    };
    mergeRegeneratedItem(local, {
      errorMessage: 'AI 模型调用失败',
      previewItemId: 'a',
      sourceQuestionId: 1,
      success: false,
    });
    expect(local.success).toBe(false);
  });
});

describe('createTolerantRequest', () => {
  it('treats occasional failures as pending and gives up after the limit', async () => {
    let calls = 0;
    const request = createTolerantRequest(async () => {
      calls += 1;
      if (calls === 1 || calls >= 3) throw new Error('network');
      return 'ok';
    }, 2);
    await expect(request()).resolves.toBeUndefined();
    await expect(request()).resolves.toBe('ok');
    await expect(request()).resolves.toBeUndefined();
    await expect(request()).rejects.toThrow('network');
  });
});
