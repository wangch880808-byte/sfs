import type { EduDataExchangeApi } from '#/api/edu/data-exchange';

/**
 * 预检时跳过一行的原因（后端写在每行数据的 skipReason 里，批次的 skipReasonCounts 按它计数）。
 * 目前只有成绩册导入细分原因，其他导入只显示跳过总数。
 */
const SKIP_REASONS: Record<string, { label: string; short: string }> = {
  UNCHANGED: { label: '成绩和评语都没有改动', short: '未改动' },
  EMPTY: { label: '待批改，没有填写成绩', short: '未填成绩' },
  PRE_REDO_SCORE: {
    label: '旧版成绩册里的打回前成绩，不作为新成绩',
    short: '打回前成绩',
  },
  AWAITING_RESUBMIT: { label: '已打回，等学生重新提交', short: '等待重新提交' },
  NOT_SUBMITTED: { label: '学生还没有提交', short: '未提交' },
};

const SKIP_REASON_ORDER = Object.keys(SKIP_REASONS);

export interface ImportSkipBreakdownItem {
  count: number;
  key: string;
  label: string;
}

/** 单行的跳过原因，用在预检明细表里 */
export function getSkipReasonLabel(reason: unknown) {
  if (typeof reason !== 'string' || !reason) return '—';
  return SKIP_REASONS[reason]?.label ?? '其他原因';
}

/** 批次的跳过行按原因拆分，按固定顺序排列，只保留数量大于 0 的 */
export function getSkipBreakdown(
  counts?: Record<string, number>,
): ImportSkipBreakdownItem[] {
  if (!counts) return [];
  const keys = [
    ...SKIP_REASON_ORDER.filter((key) => key in counts),
    ...Object.keys(counts).filter((key) => !SKIP_REASON_ORDER.includes(key)),
  ];
  return keys
    .map((key) => ({
      count: Number(counts[key]) || 0,
      key,
      label: SKIP_REASONS[key]?.short ?? '其他原因',
    }))
    .filter((item) => item.count > 0);
}

/** 「未改动 3 · 未填成绩 2」，没有细分原因时返回空字符串 */
export function formatSkipBreakdown(counts?: Record<string, number>) {
  return getSkipBreakdown(counts)
    .map((item) => `${item.label} ${item.count}`)
    .join(' · ');
}

/** 预检通过但没有任何要新建或更新的行：确认导入不会改动数据 */
export function hasNothingToWrite(batch?: EduDataExchangeApi.ImportBatch) {
  return (
    !!batch &&
    (batch.invalidCount ?? 0) === 0 &&
    (batch.insertedCount ?? 0) === 0 &&
    (batch.updatedCount ?? 0) === 0
  );
}
