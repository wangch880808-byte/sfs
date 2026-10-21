/**
 * 页面自己提示请求失败时，读出失败原因（请求选项 silentError: true 之后，全局不再弹提示）。
 *
 * 请求库遇到业务错误时抛出响应体 { code, msg, data }；断网和超时抛 axios 错误。
 * 返回不带句末标点的一句话，方便接在「没有成功：」后面；读不出原因时返回空字符串。
 */

const MUTATION_METHODS = new Set(['DELETE', 'PATCH', 'POST', 'PUT']);
const HAS_CHINESE = /\p{Script=Han}/u;
const TRAILING_PUNCTUATION = /[!.?。！？]+$/;

interface RequestErrorLike {
  code?: unknown;
  config?: { method?: unknown };
  data?: { msg?: unknown };
  message?: unknown;
  msg?: unknown;
  response?: { data?: { msg?: unknown } };
}

function trimSentence(text: string) {
  return text.trim().replace(TRAILING_PUNCTUATION, '');
}

export function getRequestErrorReason(error: unknown): string {
  if (!error || typeof error !== 'object') {
    return '';
  }
  const source = error as RequestErrorLike;

  for (const msg of [
    source.msg,
    source.data?.msg,
    source.response?.data?.msg,
  ]) {
    if (typeof msg === 'string' && msg.trim()) {
      return trimSentence(msg);
    }
  }

  const message = typeof source.message === 'string' ? source.message : '';
  const isMutation = MUTATION_METHODS.has(
    String(source.config?.method ?? '').toUpperCase(),
  );
  const isNetwork = /network error/i.test(message);
  const isTimeout = /timeout/i.test(message);
  if (isNetwork || isTimeout) {
    if (isMutation) {
      return '网络中断或响应超时，结果没有确认';
    }
    return isNetwork ? '网络连接中断' : '服务器响应超时';
  }

  // 页面或接口函数自己抛出的中文原因；英文的技术信息不直接给用户看
  return HAS_CHINESE.test(message) ? trimSentence(message) : '';
}
