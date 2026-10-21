import type { Dayjs } from 'dayjs';

import type { ComputedRef, Ref } from 'vue';

import type { useGradingDraft } from './use-grading-draft';
import type { useGradingQueue } from './use-grading-queue';
import type { useGradingSelection } from './use-grading-selection';
import type { useGradingUndo } from './use-grading-undo';

import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { ref } from 'vue';

import { prompt } from '@vben/common-ui';

import { DatePicker, message } from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  correctHomeworkRecord,
  redoHomeworkRecord,
  redoHomeworkRecordBatch,
} from '#/api/edu/homework-record';
import {
  getDefaultRedoDeadline,
  isValidRedoDeadline,
  REDO_DEADLINE_FORMAT,
} from '#/utils/homework-deadline';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { isRedoStatus } from '#/utils/status-grading';

import { getRequestErrorMessage, isGradingConflict } from './grading-errors';
import { getNextRecordId } from './use-grading-queue';

/**
 * 批改页的写操作：保存（并看下一份）、打回与调整重做期限、批量打回、撤销、冲突后刷新。
 * 单份操作带 expectedUpdateTime，出错不弹全局提示：冲突写在表单横幅里，其他错误提示一次。
 * 成功后只改当前一行与右栏详情，不整表重读。
 */
const SILENT = { silentError: true } as const;
export const REDO_BATCH_LIMIT = 200;

interface GradingActionContext {
  canCorrect: ComputedRef<boolean>;
  checkedIds: Ref<number[]>;
  current: ComputedRef<EduHomeworkRecordApi.HomeworkRecord | undefined>;
  drafts: ReturnType<typeof useGradingDraft>;
  homeworkDeadline: Ref<number | string | undefined>;
  queue: ReturnType<typeof useGradingQueue>;
  reloadQueue: () => Promise<void>;
  select: (id: number | undefined) => void;
  selection: ReturnType<typeof useGradingSelection>;
  undo: ReturnType<typeof useGradingUndo>;
}

export function getStudentName(record?: {
  studentName?: string;
  studentNo?: string;
}) {
  return record?.studentName || record?.studentNo || '未命名学生';
}

/**
 * 选择打回重做的截止时间：默认 max(当前时间, 基准时间) + 7 天；期限必须晚于当前时间。
 * 标题写对象和动作，正文写后果和怎么恢复；取消时返回 undefined。
 */
async function promptRedoDeadline(
  title: string,
  content: string,
  baseDeadline: null | number | string | undefined,
  confirmText: string,
) {
  try {
    return await prompt<string>({
      beforeClose: ({ isConfirm, value }) => {
        if (isConfirm && !isValidRedoDeadline(value)) {
          message.warning('重做截止时间必须晚于当前时间');
          return false;
        }
      },
      cancelText: '返回',
      component: DatePicker,
      componentProps: {
        allowClear: false,
        class: 'w-full',
        disabledDate: (day: Dayjs) => day.isBefore(dayjs(), 'day'),
        format: 'YYYY-MM-DD HH:mm',
        placeholder: '请选择重做截止时间',
        // 提示框是屏蔽外部指针事件的模态层（z-index 2000），日期面板需浮在其上并可点击
        popupStyle: { pointerEvents: 'auto', zIndex: 2001 },
        showTime: { format: 'HH:mm' },
        valueFormat: REDO_DEADLINE_FORMAT,
      },
      confirmText,
      content,
      defaultValue: getDefaultRedoDeadline(baseDeadline),
      modelPropName: 'value',
      title,
    });
  } catch {
    return undefined;
  }
}

export function useGradingActions(ctx: GradingActionContext) {
  const { drafts, queue, selection, undo } = ctx;
  const submitting = ref(false);
  const refreshing = ref(false);
  /** 保存冲突的说明，显示在批改表单上方 */
  const conflictText = ref('');
  /** 保存、撤销、刷新后加一：右栏的成绩轨迹与 AI 初评据此重读 */
  const reloadToken = ref(0);

  function patchRow(resp: EduHomeworkRecordApi.GradeResp) {
    queue.patch(resp.id, {
      aiReviewStatus: resp.aiReviewStatus,
      correctTime: resp.correctTime,
      redoCount: resp.redoCount,
      redoDeadline: resp.redoDeadline,
      score: resp.score,
      status: resp.status,
      updateTime: resp.updateTime,
    });
  }

  function applyResult(resp: EduHomeworkRecordApi.GradeResp) {
    patchRow(resp);
    selection.applySaved(resp);
    reloadToken.value++;
  }

  function handleError(error: unknown, failed: string) {
    if (isGradingConflict(error)) {
      conflictText.value = getRequestErrorMessage(
        error,
        '这份提交已被他人更新，请刷新后再看',
      );
      return;
    }
    message.error(getRequestErrorMessage(error, `${failed}，请稍后重试。`));
  }

  async function save(goNext: boolean) {
    const id = selection.selectedId.value;
    const row = selection.selectedRow.value;
    const { form } = selection;
    if (!id || !row || submitting.value) return;
    if (!selection.formReady.value || !ctx.canCorrect.value) return;
    if (!selection.validate()) return;
    // 保存前按当前筛选顺序定下一份：保存后这一行可能不再符合筛选
    const nextId = goNext
      ? getNextRecordId(queue.filteredRecords.value, id)
      : undefined;
    const saved = { comment: form.comment, score: form.score };
    submitting.value = true;
    conflictText.value = '';
    try {
      const resp = await correctHomeworkRecord(
        {
          comment: form.comment,
          correctType: 1,
          expectedUpdateTime: selection.expectedUpdateTime.value,
          id,
          score: form.score as number,
        },
        SILENT,
      );
      drafts.clear(id);
      applyResult(resp);
      undo.present(resp, 'correct', getStudentName(row), saved);
      if (!resp.undoToken) message.success('批改已保存');
      if (goNext && nextId !== undefined) ctx.select(nextId);
      else if (goNext) message.info('这已是当前筛选里的最后一份。');
    } catch (error) {
      handleError(error, '批改没有保存');
    } finally {
      submitting.value = false;
    }
  }

  /** 冲突后重读这一份：表单里已填的内容保留 */
  async function refreshConflict() {
    refreshing.value = true;
    try {
      await selection.refreshDetail();
      const fresh = selection.detail.value;
      if (!fresh?.id) return;
      queue.patch(fresh.id, {
        redoDeadline: fresh.redoDeadline,
        score: fresh.score,
        status: fresh.status ?? HOMEWORK_RECORD_STATUS.SUBMITTED,
        updateTime: fresh.updateTime,
      });
      conflictText.value = '';
      reloadToken.value++;
    } finally {
      refreshing.value = false;
    }
  }

  async function undoLast() {
    const offer = undo.offer.value;
    if (!offer) return;
    const restored = await undo.undo();
    if (!restored) return;
    patchRow(restored);
    if (offer.kind === 'correct' && offer.saved) {
      // 撤销后把刚才填的分数和评语放回草稿，改完再保存
      drafts.save(restored.id, offer.saved, {
        comment: restored.comment ?? '',
        score: restored.score,
      });
    }
    if (selection.selectedId.value === restored.id) {
      selection.applySaved(restored);
      await selection.refreshDetail();
    } else {
      ctx.select(restored.id);
    }
    reloadToken.value++;
    message.success(`已撤销，「${offer.studentName}」的提交恢复到操作前`);
  }

  async function redo() {
    const record = ctx.current.value;
    const id = selection.selectedId.value;
    if (!record || !id || submitting.value) return;
    const adjusting = isRedoStatus(record.status);
    const name = getStudentName(record);
    const redoDeadline = adjusting
      ? await promptRedoDeadline(
          `调整「${name}」的重做期限？`,
          '这份提交已打回，这次只更新重做截止时间，不会再次通知学生。请选择新的重做截止时间：',
          record.redoDeadline ?? ctx.homeworkDeadline.value,
          '调整重做期限',
        )
      : await promptRedoDeadline(
          `打回「${name}」的提交？`,
          '打回后会通知学生重做，当前成绩保留为原成绩，AI 初评随之作废；打回后几秒内可以撤销。学生重新提交后可以再次批改，期间可以调整重做期限。请选择重做截止时间：',
          ctx.homeworkDeadline.value,
          '打回',
        );
    if (!redoDeadline) return;
    submitting.value = true;
    conflictText.value = '';
    try {
      const resp = await redoHomeworkRecord(
        {
          expectedUpdateTime: selection.expectedUpdateTime.value,
          id,
          redoDeadline,
        },
        SILENT,
      );
      applyResult(resp);
      undo.present(resp, 'redo', name);
      if (!resp.undoToken) {
        message.success(adjusting ? '重做截止时间已更新' : '已打回重做');
      }
    } catch (error) {
      handleError(error, adjusting ? '重做期限没有更新' : '打回没有完成');
    } finally {
      submitting.value = false;
    }
  }

  async function redoBatch() {
    const ids = [...ctx.checkedIds.value];
    if (ids.length === 0) return;
    if (ids.length > REDO_BATCH_LIMIT) {
      message.warning(
        `一次最多打回 ${REDO_BATCH_LIMIT} 份，现在选了 ${ids.length} 份。少选一些再打回。`,
      );
      return;
    }
    const adjustCount = ids.filter((id) =>
      isRedoStatus(queue.findRecord(id)?.status),
    ).length;
    const adjustTip =
      adjustCount > 0
        ? `其中 ${adjustCount} 份已打回，只调整重做截止时间。`
        : '';
    const redoDeadline = await promptRedoDeadline(
      `打回所选的 ${ids.length} 份提交？`,
      `打回后会通知这些学生重做，当前成绩保留为原成绩。${adjustTip}学生重新提交后可以再次批改，期间可以调整重做期限。请选择重做截止时间：`,
      ctx.homeworkDeadline.value,
      `打回 ${ids.length} 份`,
    );
    if (!redoDeadline) return;
    submitting.value = true;
    try {
      // 批量操作沿用全局错误提示；成功后整表重读
      await redoHomeworkRecordBatch({ ids, redoDeadline });
      message.success(`已打回 ${ids.length} 份提交`);
      ctx.checkedIds.value = [];
      await ctx.reloadQueue();
    } finally {
      submitting.value = false;
    }
  }

  return {
    conflictText,
    redo,
    redoBatch,
    refreshConflict,
    refreshing,
    reloadToken,
    save,
    submitting,
    undoLast,
  };
}
