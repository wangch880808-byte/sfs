import type { AiReviewApi } from '#/api/edu/ai-review';

export const AI_REVIEW_TEXT = {
  calibrationLoading: '正在读取 AI 评分校准状态…',
  calibrationLoadFailed: 'AI 评分校准状态没有读取成功。',
  draftTriggered: '已触发 AI 初评，生成后可填入批改表单',
  hideHint: '收起提示原文',
  pollingTimeout: 'AI 仍在处理，可稍后刷新查看',
  preReviewStatusFailed: 'AI 初评状态没有读取成功，稍后重试。',
  refresh: '手动刷新',
  retry: '重试',
  showHint: '查看提示原文',
  taskFailure: 'AI 初评没有全部生成成功，失败原因见生成进度。',
} as const;

const SCOPE_LABELS: Record<AiReviewApi.CalibrationScope, string> = {
  COURSE: '同一课程',
  NONE: '近期',
  TASK: '当前任务',
};

export function getAiCalibrationDisplay(
  calibration: AiReviewApi.CalibrationRespVO,
) {
  const scope = SCOPE_LABELS[calibration.scope];
  const samples = `${scope} ${calibration.sampleCount} 条样本`;
  const deviation = Math.abs(calibration.averageDeviation).toFixed(1);
  let deviationText = '平均与 AI 持平';
  if (calibration.averageDeviation < 0) {
    deviationText = `平均比 AI 低 ${deviation} 分`;
  } else if (calibration.averageDeviation > 0) {
    deviationText = `平均比 AI 高 ${deviation} 分`;
  }
  switch (calibration.direction) {
    case 'INSUFFICIENT': {
      return { color: 'default', text: `样本不足，暂不校准（${samples}）` };
    }
    case 'LOOSER': {
      return {
        color: 'green',
        text: `AI 评分已按你近期的反馈适度从宽（${samples}，${deviationText}）`,
      };
    }
    case 'STRICTER': {
      return {
        color: 'blue',
        text: `AI 评分已按你近期的反馈适度从严（${samples}，${deviationText}）`,
      };
    }
    default: {
      return {
        color: 'default',
        text: `你近期的反馈没有明显的从严或从宽倾向，无需校准（${samples}）`,
      };
    }
  }
}
