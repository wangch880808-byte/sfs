import type { SavedGrade, useGradingDraft } from './use-grading-draft';
import type { QueueRecord } from './use-grading-queue';

import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { computed, reactive, ref, watch } from 'vue';

import { getHomeworkRecord } from '#/api/edu/homework-record';

/**
 * 当前选中的提交：读取详情、批改表单与草稿。
 * 切换学生前已输入的内容随时写进草稿；回到这份提交时先恢复草稿，再用详情更新“已保存的成绩”。
 */
export interface GradeErrors {
  comment: string;
  score: string;
}

/** 评分与评语的校验，结果写在输入框下面 */
export function validateGrade(
  score: null | number | undefined,
  comment: string,
): GradeErrors {
  let scoreError = '';
  if (score === null || score === undefined || Number.isNaN(score)) {
    scoreError = '请填写评分，0～100 分。';
  } else if (!Number.isInteger(score)) {
    scoreError = '评分要是整数。';
  } else if (score < 0 || score > 100) {
    scoreError = '评分要在 0～100 之间。';
  }
  const commentError = comment.trim() ? '' : '请填写评语，学生会在反馈里看到。';
  return { comment: commentError, score: scoreError };
}

type Drafts = ReturnType<typeof useGradingDraft>;

export function useGradingSelection(
  findRow: (id: number | undefined) => QueueRecord | undefined,
  drafts: Drafts,
) {
  const selectedId = ref<number>();
  const detail = ref<EduHomeworkRecordApi.HomeworkRecord>();
  const detailLoading = ref(false);
  const detailError = ref(false);
  /** 表单只在详情读到后可编辑，避免读到详情时覆盖正在输入的内容 */
  const formReady = ref(false);
  const form = reactive<{ comment: string; score: null | number }>({
    comment: '',
    score: null,
  });
  const errors = reactive<GradeErrors>({ comment: '', score: '' });
  const baseline = ref<SavedGrade>({});
  let requestId = 0;

  /** 列表行（状态、分数随保存即时更新）与详情（作答内容、评语）合在一起给页面用 */
  const selectedRow = computed(() => findRow(selectedId.value));
  const expectedUpdateTime = computed(
    () => detail.value?.updateTime ?? selectedRow.value?.updateTime,
  );

  function clearErrors() {
    errors.score = '';
    errors.comment = '';
  }

  function setForm(score: null | number | undefined, comment?: null | string) {
    form.score = score ?? null;
    form.comment = comment ?? '';
  }

  async function loadDetail(id: number, keepForm: boolean) {
    const current = ++requestId;
    detailLoading.value = true;
    detailError.value = false;
    try {
      const result = await getHomeworkRecord(id, { silentError: true });
      if (current !== requestId) return;
      detail.value = result;
      baseline.value = { comment: result.comment ?? '', score: result.score };
      if (!keepForm) setForm(result.score, result.comment);
      formReady.value = true;
      // 详情读到后如果草稿与已保存的成绩一致，就不再标记草稿
      drafts.save(id, { ...form }, baseline.value);
    } catch {
      if (current === requestId) detailError.value = true;
    } finally {
      if (current === requestId) detailLoading.value = false;
    }
  }

  function select(id: number | undefined) {
    if (id === selectedId.value && detail.value) return;
    formReady.value = false;
    selectedId.value = id;
    detail.value = undefined;
    detailError.value = false;
    clearErrors();
    if (id === undefined) {
      ++requestId;
      detailLoading.value = false;
      setForm(null, '');
      return;
    }
    const draft = drafts.load(id);
    if (draft) setForm(draft.score, draft.comment);
    else setForm(findRow(id)?.score, '');
    void loadDetail(id, Boolean(draft));
  }

  /** 冲突或读取失败后重读详情；表单里已填的内容保留 */
  async function refreshDetail() {
    const id = selectedId.value;
    if (id === undefined) return;
    const keep = formReady.value || Boolean(drafts.load(id));
    formReady.value = false;
    await loadDetail(id, keep);
  }

  /** 保存、打回、撤销成功后同步详情与“已保存的成绩” */
  function applySaved(resp: EduHomeworkRecordApi.GradeResp) {
    if (resp.id !== selectedId.value || !detail.value) return;
    detail.value = {
      ...detail.value,
      comment: resp.comment ?? undefined,
      correctTime: resp.correctTime ?? undefined,
      correctType: resp.correctType ?? undefined,
      redoCount: resp.redoCount,
      redoDeadline: resp.redoDeadline ?? undefined,
      score: resp.score ?? undefined,
      status: resp.status,
      updateTime: resp.updateTime,
    } as EduHomeworkRecordApi.HomeworkRecord;
    baseline.value = { comment: resp.comment ?? '', score: resp.score };
  }

  function validate(): boolean {
    const result = validateGrade(form.score, form.comment);
    errors.score = result.score;
    errors.comment = result.comment;
    return !result.score && !result.comment;
  }

  watch(
    () => [form.score, form.comment] as const,
    ([score, comment]) => {
      if (!formReady.value || selectedId.value === undefined) return;
      if (errors.score || errors.comment) {
        const result = validateGrade(score, comment);
        if (!result.score) errors.score = '';
        if (!result.comment) errors.comment = '';
      }
      drafts.save(selectedId.value, { comment, score }, baseline.value);
    },
    { flush: 'sync' },
  );

  return {
    applySaved,
    detail,
    detailError,
    detailLoading,
    errors,
    expectedUpdateTime,
    form,
    formReady,
    refreshDetail,
    select,
    selectedId,
    selectedRow,
    validate,
  };
}
