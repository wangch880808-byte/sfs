import type { StatusMeta } from './status';

/**
 * 基础设施、AI 控制台、个人中心与数据导入用到的状态（V1.0.1.6 界面规范）：每个状态给出文字与类别，
 * 页面用 StatusTag 显示。通用的状态映射见 utils/status.ts。
 */

const UNKNOWN: StatusMeta = { label: '未知', tone: 'neutral' };

/** API 访问日志的结果（infra_api_access_log.result_code）：0 为成功，其余是错误码 */
export function getApiAccessResultMeta(
  resultCode: null | number | string | undefined,
): StatusMeta {
  if (resultCode === null || resultCode === undefined || resultCode === '') {
    return UNKNOWN;
  }
  return Number(resultCode) === 0
    ? { label: '成功', tone: 'done' }
    : { label: '失败', tone: 'failed' };
}

/** 业务模型绑定：选中的模型可用即“可用”，没选或模型已停用为“未就绪”（等管理员处理） */
export function getModelBindingMeta(available: boolean): StatusMeta {
  return available
    ? { label: '可用', tone: 'done' }
    : { label: '未就绪', tone: 'waiting' };
}

/** 第三方账号绑定（钉钉、微信小程序等） */
export function getSocialBindingMeta(bound: boolean): StatusMeta {
  return bound
    ? { label: '已绑定', tone: 'done' }
    : { label: '未绑定', tone: 'neutral' };
}

/** 数据导入预检的逐行结果（VALID 通过，INVALID 错误） */
export function getImportValidationMeta(
  status: null | string | undefined,
): StatusMeta {
  if (status === 'VALID') {
    return { label: '通过', tone: 'done' };
  }
  if (status === 'INVALID') {
    return { label: '错误', tone: 'failed' };
  }
  return UNKNOWN;
}
