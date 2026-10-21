/**
 * 学生端详情页读取失败时的分类（V1.0.1.6 界面规范：出错按“什么没成功—原因—下一步”写；
 * 前置条件不满足（等级未解锁）单独说明原因和怎么解锁，不再只弹一条提示留下空白页）。
 *
 * 请求库遇到业务错误时直接抛出响应体 { code, msg, data }（request-client 抛的是 error.response.data）；
 * 也兼容响应体挂在 error.data 或 error.response.data 上的写法。
 */

/** 后端错误码：该等级尚未解锁（STUDENT_COURSE_LEVEL_LOCKED） */
export const LEVEL_LOCKED_CODE = 1_050_007_001;

/** 后端错误码：课程或任务不存在，或没有分配给当前学生 */
const UNAVAILABLE_CODES = new Set([
  // 课程不存在或未分配
  1_050_007_000,
  // 任务不存在
  1_050_008_000,
  // 不在任务所在班级
  1_050_008_001,
  // 没有被分配该任务
  1_050_008_002,
]);

const DEFAULT_LOCKED_REASON = '该等级尚未解锁，请先完成上一等级任务。';

export type StudentLoadErrorKind = 'failed' | 'locked' | 'unavailable';

export interface StudentLoadError {
  kind: StudentLoadErrorKind;
  /** 后端给出的原因（补全句号）；不知道原因时为空 */
  reason: string;
}

interface BusinessErrorBody {
  code?: unknown;
  msg?: unknown;
}

function isNumericCode(code: unknown) {
  return (
    code !== undefined &&
    code !== null &&
    code !== '' &&
    Number.isFinite(Number(code))
  );
}

function isBusinessBody(value: unknown): value is BusinessErrorBody {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const body = value as BusinessErrorBody;
  return typeof body.msg === 'string' || isNumericCode(body.code);
}

/** 读出业务错误码与提示；网络错误（axios 的 code 是 ERR_NETWORK 这类字符串）读不出错误码 */
function readBusinessError(error: unknown): { code?: number; msg: string } {
  if (!error || typeof error !== 'object') {
    return { msg: '' };
  }
  const source = error as { data?: unknown; response?: { data?: unknown } };
  for (const body of [error, source.data, source.response?.data]) {
    if (isBusinessBody(body)) {
      return {
        code: isNumericCode(body.code) ? Number(body.code) : undefined,
        msg: typeof body.msg === 'string' ? body.msg : '',
      };
    }
  }
  return { msg: '' };
}

/** 句末补全角句号 */
export function toSentence(text: string): string {
  const trimmed = text.trim();
  if (!trimmed) {
    return '';
  }
  return /[!.?。！？]$/.test(trimmed) ? trimmed : `${trimmed}。`;
}

/** 断网、超时：页面的请求不弹全局提示后，由页面写明原因 */
function readTransportReason(error: unknown): string {
  const message =
    error && typeof error === 'object'
      ? String((error as { message?: unknown }).message ?? '')
      : '';
  if (/network error/i.test(message)) {
    return '网络连接中断。';
  }
  if (/timeout/i.test(message)) {
    return '服务器响应超时。';
  }
  return '';
}

/** 读取失败的类别与原因：等级未解锁、不存在或没有分配、其他失败 */
export function resolveStudentLoadError(error: unknown): StudentLoadError {
  const { code, msg } = readBusinessError(error);
  if (code === LEVEL_LOCKED_CODE) {
    return { kind: 'locked', reason: toSentence(msg) || DEFAULT_LOCKED_REASON };
  }
  if (code !== undefined && UNAVAILABLE_CODES.has(code)) {
    return { kind: 'unavailable', reason: '' };
  }
  return {
    kind: 'failed',
    reason: toSentence(msg) || readTransportReason(error),
  };
}

/** 等级解锁状态里某个等级的一项（V1.0.1.7 起带 reasonText，旧版后端没有） */
export interface LevelUnlockItem {
  level?: null | number;
  reasonText?: null | string;
  unlocked?: boolean | null;
}

/**
 * 未解锁等级的具体原因（如“还差 2 个任务达标。”）；找不到该等级、已解锁或没有原因时返回空字符串。
 */
export function getLevelLockedDetail(
  levels: LevelUnlockItem[] | null | undefined,
  level: null | number | undefined,
): string {
  if (level === undefined || level === null || !Array.isArray(levels)) {
    return '';
  }
  const item = levels.find((entry) => Number(entry.level) === Number(level));
  if (!item || item.unlocked) {
    return '';
  }
  return toSentence(String(item.reasonText ?? ''));
}
