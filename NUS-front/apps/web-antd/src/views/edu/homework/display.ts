/**
 * 实训任务管理页的呈现辅助（V1.0.1.6 界面规范）：确认框文案、空状态判断，以及详情页的时间、比例与批改方式文字。
 * 只做显示上的换算，不改接口与数据。确认框的写法：标题写对象和动作，正文写后果和怎么恢复；
 * 确认按钮重复动作名，取消按钮写“返回”。
 */
import { ARCHIVE_COPY } from '#/utils/archive';
import { HOMEWORK_STATUS, isHomeworkArchived } from '#/utils/status';

import { GRADING_STRATEGY_OPTIONS, resolveGradingStrategy } from './data';

export const CONFIRM_CANCEL_TEXT = '返回';

/** 封存的后果与恢复办法 */
export const HOMEWORK_ARCHIVE_HINT = `${ARCHIVE_COPY.archiveHint}。`;

/** 恢复的结果 */
export const HOMEWORK_RESTORE_HINT = '恢复到封存前的状态，可以继续修改和批改。';

/** 旧数据里的草稿（状态 0）：列表显示为「待发布」，编辑并保存后由后端发布 */
export const HOMEWORK_PENDING_LABEL = '待发布';
export const HOMEWORK_PENDING_HINT = '编辑并保存后发布';

/** 待发布（旧草稿）：按数字比较，字符串 '0' 也算；空值不算 */
export function isHomeworkPending(
  status: null | number | string | undefined,
): boolean {
  if (status === null || status === undefined || status === '') {
    return false;
  }
  return Number(status) === HOMEWORK_STATUS.DRAFT;
}

/**
 * 列表查询是否带上已封存的任务：开了「显示已封存」，或状态筛选选的就是「已封存」。
 * 只选了已封存却没开开关时，结果会永远为空，所以按选了处理。
 */
export function resolveIncludeArchived(
  status: unknown,
  includeArchived: boolean,
): boolean {
  if (includeArchived) {
    return true;
  }
  if (status === null || status === undefined || status === '') {
    return false;
  }
  return isHomeworkArchived(status as number | string);
}

/** 行内气泡确认（TableAction 的 popConfirm，标题与正文分两行） */
export interface RowConfirmCopy {
  cancelText: string;
  description: string;
  okText: string;
  title: string;
}

/** 对话框确认（@vben/common-ui 的 confirm） */
export interface BatchConfirmCopy {
  cancelText: string;
  confirmText: string;
  content: string;
  title: string;
}

/** 行内操作：“封存实训任务「组件化重构」？” */
export function homeworkRowConfirm(
  action: string,
  name: null | string | undefined,
  description: string,
): RowConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    description,
    okText: action,
    title: name ? `${action}实训任务「${name}」？` : `${action}这个实训任务？`,
  };
}

/** 批量操作：“封存选中的 3 个实训任务？” */
export function homeworkBatchConfirm(
  action: string,
  count: number,
  description: string,
): BatchConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    confirmText: action,
    content: description,
    title: `${action}选中的 ${count} 个实训任务？`,
  };
}

function isFilled(value: unknown): boolean {
  if (value === undefined || value === null) {
    return false;
  }
  if (typeof value === 'string') {
    return value.trim() !== '';
  }
  if (Array.isArray(value)) {
    return value.some((item) => isFilled(item));
  }
  return true;
}

/** 列表的筛选条件是否生效：决定空状态写“还没有实训任务”还是“没有符合条件的实训任务” */
export function hasActiveFilters(
  values?: null | Record<string, unknown>,
): boolean {
  return !!values && Object.values(values).some((value) => isFilled(value));
}

/** 时间显示到分钟：支持时间戳（秒或毫秒）、数字字符串与日期字符串；空值显示“-” */
export function formatDateTime(value?: Date | null | number | string): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const raw =
    typeof value === 'string' && /^\d+$/.test(value.trim())
      ? Number(value.trim())
      : value;
  let normalized = raw;
  if (typeof raw === 'number' && Number.isFinite(raw) && raw < 1e12) {
    normalized = raw * 1000;
  }
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 比例取整百分比；总数为 0 时显示 0% */
export function formatRate(value: number, total: number): string {
  if (total <= 0) {
    return '0%';
  }
  return `${Math.round((value / total) * 100)}%`;
}

/**
 * 批改方式的文字，取自新建表单的批改方式选项（GRADING_STRATEGY_OPTIONS），只保留这一个来源：
 * 未开自动批改为“仅人工批改”，开了即为“AI 初评 + 教师复核”。早期 aiReviewMode=0 的任务同样按 AI 初评显示。
 */
export function gradingStrategyLabel(
  autoGrade?: null | number,
  aiReviewMode?: null | number,
): string {
  const strategy = resolveGradingStrategy(
    autoGrade ?? undefined,
    aiReviewMode ?? undefined,
  );
  return (
    GRADING_STRATEGY_OPTIONS.find((item) => item.value === strategy)?.label ??
    '-'
  );
}
