import type { GradingDraft } from './use-grading-draft';

import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { onBeforeUnmount, ref } from 'vue';

import { undoHomeworkGrading } from '#/api/edu/homework-record';

import { getRequestErrorMessage } from './grading-errors';

/**
 * 保存批改、打回后的撤销：按后端给的剩余秒数倒计时（不依赖本机时钟与服务器对时），
 * 到点或换了一次新操作就收起。撤销失败的原因就地显示。
 */
export type UndoKind = 'correct' | 'redo';

export interface UndoOffer {
  kind: UndoKind;
  recordId: number;
  remaining: number;
  /** 批改时填的分数与评语：撤销后放回草稿，不用重新输入 */
  saved?: GradingDraft;
  studentName: string;
  token: number;
}

/** 底部状态行：“已保存「张三」的批改”，按钮“撤销（8 秒）” */
export function getUndoText(
  offer: Pick<UndoOffer, 'kind' | 'remaining' | 'studentName'>,
) {
  const subject = `「${offer.studentName}」`;
  return {
    action: `撤销（${offer.remaining} 秒）`,
    text:
      offer.kind === 'redo'
        ? `已打回${subject}的提交`
        : `已保存${subject}的批改`,
  };
}

export function useGradingUndo(now: () => number = () => Date.now()) {
  const offer = ref<UndoOffer>();
  const undoing = ref(false);
  const errorText = ref('');
  let timer: ReturnType<typeof setInterval> | undefined;
  let deadline = 0;

  function stopTimer() {
    if (timer) clearInterval(timer);
    timer = undefined;
  }

  function clear() {
    stopTimer();
    offer.value = undefined;
  }

  function tick() {
    if (!offer.value) return;
    const remaining = Math.ceil((deadline - now()) / 1000);
    if (remaining <= 0) {
      clear();
      return;
    }
    offer.value = { ...offer.value, remaining };
  }

  /** 保存或打回成功后调用；没有撤销凭据（撤销时长为 0、只调整期限）时不显示 */
  function present(
    resp: EduHomeworkRecordApi.GradeResp,
    kind: UndoKind,
    studentName: string,
    saved?: GradingDraft,
  ) {
    stopTimer();
    errorText.value = '';
    const seconds = Number(resp.undoSeconds ?? 0);
    if (!resp.undoToken || !Number.isFinite(seconds) || seconds <= 0) {
      offer.value = undefined;
      return;
    }
    deadline = now() + seconds * 1000;
    offer.value = {
      kind,
      recordId: resp.id,
      remaining: seconds,
      saved,
      studentName,
      token: resp.undoToken,
    };
    timer = setInterval(tick, 1000);
  }

  /** 撤销成功返回恢复后的记录；失败返回空，原因在 errorText */
  async function undo(): Promise<EduHomeworkRecordApi.GradeResp | undefined> {
    const current = offer.value;
    if (!current || undoing.value) return undefined;
    undoing.value = true;
    errorText.value = '';
    try {
      const restored = await undoHomeworkGrading(current.token, {
        silentError: true,
      });
      clear();
      return restored;
    } catch (error) {
      errorText.value = getRequestErrorMessage(
        error,
        '撤销没有完成：网络不稳定，请刷新这份提交查看当前状态。',
      );
      clear();
      return undefined;
    } finally {
      undoing.value = false;
    }
  }

  onBeforeUnmount(stopTimer);

  return { clear, errorText, offer, present, undo, undoing };
}
