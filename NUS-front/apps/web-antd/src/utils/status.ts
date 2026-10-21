import type { StatusTone } from '#/styles/theme/palette';

/**
 * 业务状态与界面呈现的集中对应（V1.0.1.6 界面规范）：每个状态给出文字与类别（决定圆点颜色），
 * 页面用 StatusTag 显示，不再各写一个 getStatusTag。类别按“该不该注意、注意什么”分：
 * neutral 未开始或已结束、running 进行中、waiting 等这个人处理、done 已完成、failed 失败。
 */
export interface StatusMeta {
  label: string;
  tone: StatusTone;
}

/** 同一个状态，对教师和学生“该谁处理”不同，类别也不同 */
export type StatusAudience = 'student' | 'teacher';

/** 实训任务状态（edu_homework.status，字典 edu_homework_status） */
export const HOMEWORK_STATUS = {
  DRAFT: 0,
  PUBLISHED: 1,
  CLOSED: 2,
  ARCHIVED: 3,
} as const;

/**
 * 实训任务已封存。按数字比较（字符串 '3' 也算），与 utils/archive.ts 的
 * isArchived(status, HOMEWORK_STATUS.ARCHIVED) 等价。
 */
export function isHomeworkArchived(
  status: null | number | string | undefined,
): boolean {
  return Number(status) === HOMEWORK_STATUS.ARCHIVED;
}

/** 提交记录状态（edu_homework_record.status） */
export const HOMEWORK_RECORD_STATUS = {
  NOT_SUBMITTED: 0,
  SUBMITTED: 1,
  CORRECTED: 2,
  REDO: 3,
  RESUBMITTED: 4,
} as const;

const RECORD_STATUS_META: Record<StatusAudience, Record<number, StatusMeta>> = {
  // 教师：待批改与重新提交等教师处理；已打回等学生重做
  teacher: {
    0: { label: '未提交', tone: 'neutral' },
    1: { label: '待批改', tone: 'waiting' },
    2: { label: '已批改', tone: 'done' },
    3: { label: '已打回', tone: 'running' },
    4: { label: '已重新提交', tone: 'waiting' },
  },
  // 学生：已打回等学生重做；提交后等教师批改
  student: {
    0: { label: '未提交', tone: 'neutral' },
    1: { label: '待批改', tone: 'running' },
    2: { label: '已批改', tone: 'done' },
    3: { label: '已打回', tone: 'waiting' },
    4: { label: '待批改', tone: 'running' },
  },
};

const UNKNOWN: StatusMeta = { label: '未知状态', tone: 'neutral' };

export function getHomeworkRecordStatusMeta(
  status: null | number | string | undefined,
  audience: StatusAudience = 'teacher',
): StatusMeta {
  if (status === null || status === undefined || status === '') {
    return UNKNOWN;
  }
  return RECORD_STATUS_META[audience][Number(status)] ?? UNKNOWN;
}

/** AI 初评状态（edu_ai_pre_review.status）；没有记录时为“未生成” */
const AI_PRE_REVIEW_STATUS_META: Record<number, StatusMeta> = {
  0: { label: '排队中', tone: 'running' },
  1: { label: '处理中', tone: 'running' },
  2: { label: '初评已生成', tone: 'waiting' },
  3: { label: '生成失败', tone: 'failed' },
  4: { label: '已按初评保存', tone: 'done' },
  5: { label: '已拒绝', tone: 'neutral' },
  6: { label: '已跳过', tone: 'neutral' },
};

export function getAiPreReviewStatusMeta(
  status: null | number | undefined,
): StatusMeta {
  if (status === null || status === undefined) {
    return { label: '未生成', tone: 'neutral' };
  }
  return (
    AI_PRE_REVIEW_STATUS_META[status] ?? { label: '未生成', tone: 'neutral' }
  );
}

/** 实训任务的进度：过了截止时间为已截止；提交都已批改为已完成；其余进行中 */
export function getTaskProgressMeta(
  task: {
    correctedCount?: null | number | string;
    deadline?: null | number | string;
    submittedCount?: null | number | string;
  },
  now: number = Date.now(),
): StatusMeta {
  if (task.deadline && new Date(task.deadline).getTime() < now) {
    return { label: '已截止', tone: 'neutral' };
  }
  const submitted = Number(task.submittedCount || 0);
  const corrected = Number(task.correctedCount || 0);
  if (submitted > 0 && corrected >= submitted) {
    return { label: '已完成', tone: 'done' };
  }
  return { label: '进行中', tone: 'running' };
}

/**
 * 状态类字典：DictTag 显示为圆点＋文字，类别按值在这里指定（字典里的颜色类型多半是装饰用的，比如性别、用户类型，
 * 不能据此判断是不是状态）；没列在这里的字典按普通文字显示。“*”是该字典其余取值的类别。
 */
const DICT_STATUS_TONES: Record<string, Record<string, StatusTone>> = {
  common_status: { '0': 'done', '1': 'neutral' },
  edu_homework_status: {
    '0': 'neutral',
    '1': 'running',
    '2': 'neutral',
    '3': 'neutral',
  },
  infra_api_error_log_process_status: {
    '0': 'waiting',
    '1': 'done',
    '2': 'neutral',
  },
  infra_job_log_status: { '0': 'running', '1': 'done', '2': 'failed' },
  infra_job_status: { '0': 'running', '1': 'done', '2': 'neutral' },
  system_login_result: { '*': 'failed', '0': 'done' },
  system_mail_send_status: {
    '0': 'running',
    '10': 'done',
    '20': 'failed',
    '30': 'neutral',
  },
  system_sms_receive_status: { '0': 'running', '10': 'done', '20': 'failed' },
  system_sms_send_status: {
    '0': 'running',
    '10': 'done',
    '20': 'failed',
    '30': 'neutral',
  },
};

/** 字典值的呈现：状态类字典给出类别，其余返回 null（按普通文字显示） */
export function getDictStatusToneOf(
  dictType: string,
  value: null | number | string | undefined,
  colorType?: null | string,
): null | StatusTone {
  const tones = DICT_STATUS_TONES[dictType];
  if (!tones) {
    return null;
  }
  const key = value === null || value === undefined ? '' : String(value);
  return tones[key] ?? tones['*'] ?? getDictStatusTone(colorType);
}

/**
 * 字典数据的颜色类型换成状态类别（状态类字典里没指定的值兜底用）：success 已完成、warning 待处理、
 * danger/error 失败、primary/processing 进行中，其余（info、default、空、自定义颜色）中性。
 */
export function getDictStatusTone(colorType?: null | string): StatusTone {
  switch (colorType) {
    case 'danger':
    case 'error': {
      return 'failed';
    }
    case 'primary':
    case 'processing': {
      return 'running';
    }
    case 'success': {
      return 'done';
    }
    case 'warning': {
      return 'waiting';
    }
    default: {
      return 'neutral';
    }
  }
}
