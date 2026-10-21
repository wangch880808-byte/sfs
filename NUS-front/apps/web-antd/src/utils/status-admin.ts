import type { StatusMeta } from './status';

import { ACCOUNT_COPY, ARCHIVE_COPY, isArchived } from './archive';

/**
 * 教务管理与系统管理的状态呈现（V1.0.1.6 界面规范）：班级封存、教务账号停用、站内信已读、三方登录密钥。
 * 类别沿用 utils/status.ts：正常与已配置为 done，已封存、已停用、已读为 neutral，未读与未配置等人处理为 waiting。
 */

const ACTIVE: StatusMeta = { label: '正常', tone: 'done' };

function toStatusNumber(status: null | number | string | undefined) {
  return typeof status === 'string' ? Number(status) : status;
}

/** 班级等可封存对象：状态值 1 为已封存，其余为正常 */
export function getArchiveStatusMeta(
  status: null | number | string | undefined,
): StatusMeta {
  return isArchived(toStatusNumber(status))
    ? { label: ARCHIVE_COPY.archived, tone: 'neutral' }
    : ACTIVE;
}

/** 教师、学生账号：状态值 1 为已停用，其余为正常 */
export function getAccountStatusMeta(
  status: null | number | string | undefined,
): StatusMeta {
  return isArchived(toStatusNumber(status))
    ? { label: ACCOUNT_COPY.disabled, tone: 'neutral' }
    : ACTIVE;
}

/** 我的站内信：未读等本人处理 */
export function getNotifyReadMeta(
  readStatus: boolean | null | undefined,
): StatusMeta {
  return readStatus
    ? { label: '已读', tone: 'neutral' }
    : { label: '未读', tone: 'waiting' };
}

/** 三方登录客户端的密钥：没配置时登录走不通，等管理员处理 */
export function getSecretConfiguredMeta(
  configured: boolean | null | undefined,
): StatusMeta {
  return configured
    ? { label: '已配置', tone: 'done' }
    : { label: '未配置', tone: 'waiting' };
}
