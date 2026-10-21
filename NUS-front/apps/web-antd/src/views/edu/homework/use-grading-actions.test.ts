import type { DraftStorage } from './use-grading-draft';

import { computed, ref } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { isCorrectableStatus } from '#/utils/status-grading';

import { useGradingActions } from './use-grading-actions';
import { useGradingDraft } from './use-grading-draft';
import { useGradingQueue } from './use-grading-queue';
import { useGradingSelection } from './use-grading-selection';
import { useGradingUndo } from './use-grading-undo';

const api = vi.hoisted(() => ({
  correct: vi.fn(),
  get: vi.fn(),
  queue: vi.fn(),
  redo: vi.fn(),
  redoBatch: vi.fn(),
  undo: vi.fn(),
}));
const ui = vi.hoisted(() => ({
  error: vi.fn(),
  info: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}));

vi.mock('#/api/edu/homework-record', () => ({
  correctHomeworkRecord: api.correct,
  getHomeworkRecord: api.get,
  getHomeworkRecordGradingQueue: api.queue,
  redoHomeworkRecord: api.redo,
  redoHomeworkRecordBatch: api.redoBatch,
  undoHomeworkGrading: api.undo,
}));
vi.mock('@vben/common-ui', () => ({ prompt: vi.fn() }));
vi.mock('ant-design-vue', () => ({ DatePicker: {}, message: ui }));
vi.mock('vue', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue')>()),
  onBeforeUnmount: vi.fn(),
}));

function memoryStorage(): DraftStorage {
  const data = new Map<string, string>();
  return {
    getItem: (key) => data.get(key) ?? null,
    key: (index) => [...data.keys()][index] ?? null,
    get length() {
      return data.size;
    },
    removeItem: (key) => void data.delete(key),
    setItem: (key, value) => void data.set(key, value),
  };
}

const rows = [
  { id: 1, status: 1, studentName: '甲', updateTime: 1000 },
  { id: 2, status: 2, studentName: '乙', score: 70, updateTime: 1000 },
  { id: 3, status: 4, studentName: '丙', updateTime: 1000 },
  { id: 4, status: 1, studentName: '丁', updateTime: 1000 },
].map((row) => ({ homeworkId: 7, studentId: row.id + 100, ...row }));

async function setup() {
  api.queue.mockResolvedValue({ list: rows, total: rows.length });
  api.get.mockImplementation(async (id: number) => ({
    ...rows.find((r) => r.id === id),
    comment: '',
    content: '答案',
  }));
  const homeworkId = ref(7);
  const queue = useGradingQueue(homeworkId);
  const drafts = useGradingDraft(homeworkId, memoryStorage());
  const selection = useGradingSelection(queue.findRecord, drafts);
  const undo = useGradingUndo();
  const current = computed(() => selection.detail.value);
  const actions = useGradingActions({
    canCorrect: computed(() => isCorrectableStatus(current.value?.status)),
    checkedIds: ref([]),
    current,
    drafts,
    homeworkDeadline: ref(),
    queue,
    reloadQueue: async () => {},
    select: (id) => selection.select(id),
    selection,
    undo,
  });
  await queue.load();
  return { actions, drafts, queue, selection, undo };
}

async function flush() {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('批改页的保存、冲突与撤销', () => {
  beforeEach(() => {
    for (const fn of [...Object.values(api), ...Object.values(ui)]) {
      fn.mockReset();
    }
  });

  it('保存并下一份：按当前筛选顺序取后一份，只改当前一行，带上读取时的更新时间', async () => {
    const { actions, queue, selection, undo } = await setup();
    queue.statusFilter.value = 1;
    selection.select(1);
    await flush();
    selection.form.score = 88;
    selection.form.comment = '结构清楚';
    api.correct.mockResolvedValue({
      comment: '结构清楚',
      id: 1,
      score: 88,
      status: 2,
      undoSeconds: 10,
      undoToken: 55,
      updateTime: 2000,
    });

    await actions.save(true);

    expect(api.correct).toHaveBeenCalledWith(
      expect.objectContaining({ expectedUpdateTime: 1000, id: 1, score: 88 }),
      { silentError: true },
    );
    expect(api.queue).toHaveBeenCalledTimes(1);
    expect(queue.findRecord(1)).toMatchObject({ score: 88, status: 2 });
    // 待批改筛选下 1 之后是 3（已重新提交），不是全表里的 2
    expect(selection.selectedId.value).toBe(3);
    expect(undo.offer.value).toMatchObject({ studentName: '甲', token: 55 });
    expect(ui.success).not.toHaveBeenCalled();
  });

  it('校验没通过不发请求；冲突写在表单横幅里，不弹提示，草稿保留', async () => {
    const { actions, drafts, selection } = await setup();
    selection.select(4);
    await flush();
    await actions.save(false);
    expect(api.correct).not.toHaveBeenCalled();
    expect(selection.errors.score).toBe('请填写评分，0～100 分。');

    selection.form.score = 60;
    selection.form.comment = '再改改';
    expect(selection.errors.score).toBe('');
    api.correct.mockRejectedValue({
      data: { code: 1_050_005_101, msg: '这份提交已被他人更新，请刷新后再看' },
    });
    await actions.save(true);
    expect(actions.conflictText.value).toBe(
      '这份提交已被他人更新，请刷新后再看',
    );
    expect(ui.error).not.toHaveBeenCalled();
    expect(selection.selectedId.value).toBe(4);
    expect(drafts.load(4)).toEqual({ comment: '再改改', score: 60 });
  });

  it('切换学生不丢已填内容，并标记草稿', async () => {
    const { drafts, selection } = await setup();
    selection.select(1);
    await flush();
    selection.form.comment = '写了一半';
    selection.select(4);
    await flush();
    expect(drafts.draftIds.value.has(1)).toBe(true);
    selection.select(1);
    await flush();
    expect(selection.form.comment).toBe('写了一半');
  });

  it('撤销后回到那份提交，刚才填的分数和评语放回草稿', async () => {
    const { actions, drafts, queue, selection, undo } = await setup();
    selection.select(1);
    await flush();
    selection.form.score = 90;
    selection.form.comment = '很好';
    api.correct.mockResolvedValue({
      comment: '很好',
      id: 1,
      score: 90,
      status: 2,
      undoSeconds: 10,
      undoToken: 55,
      updateTime: 2000,
    });
    await actions.save(true);
    expect(selection.selectedId.value).toBe(2);

    api.undo.mockResolvedValue({ id: 1, status: 1, updateTime: 3000 });
    await actions.undoLast();
    await flush();
    expect(api.undo).toHaveBeenCalledWith(55, { silentError: true });
    expect(queue.findRecord(1)?.status).toBe(1);
    expect(selection.selectedId.value).toBe(1);
    expect(drafts.load(1)).toEqual({ comment: '很好', score: 90 });
    expect(selection.form.score).toBe(90);
    expect(undo.offer.value).toBeUndefined();
  });
});
