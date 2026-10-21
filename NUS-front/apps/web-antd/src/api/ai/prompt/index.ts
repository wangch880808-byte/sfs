import { requestClient } from '#/api/request';

export namespace AiPromptTemplateApi {
  /** 每个 AI 业务的提示词现状 */
  export interface Summary {
    businessCode: string; // 业务编码
    name: string; // 提示词名称
    businessName: string; // 所属 AI 业务
    activeId?: number; // 生效版本编号；没有启用版本时为空
    activeVersion?: number; // 生效版本号
    latestVersion?: number; // 最新版本号
    content: string; // 当前生效内容
    defaultContent: string; // 内置默认内容
    usingDefault: boolean; // 没有启用版本，使用内置默认
    sameAsDefault: boolean; // 生效内容与内置默认一致
    activeTime?: string; // 启用时间
  }

  /** 提示词的一个版本 */
  export interface Version {
    id: number;
    businessCode: string;
    version: number;
    content: string;
    active: boolean;
    remark?: string;
    creatorName?: string;
    createTime: string;
  }

  /** 保存新版本 */
  export interface CreateReq {
    businessCode: string;
    content: string;
    remark?: string;
    /** 保存后立即启用，默认 true */
    activate?: boolean;
  }
}

/** 各 AI 业务的提示词现状 */
export function getPromptTemplateList() {
  return requestClient.get<AiPromptTemplateApi.Summary[]>(
    '/ai/prompt-template/list',
  );
}

/** 某个业务的提示词版本，新的在前 */
export function getPromptTemplateHistory(
  businessCode: string,
  options: { silentError?: boolean } = {},
) {
  return requestClient.get<AiPromptTemplateApi.Version[]>(
    '/ai/prompt-template/history',
    { params: { businessCode }, ...options },
  );
}

/** 保存新版本，返回新版本编号 */
export function createPromptTemplateVersion(
  data: AiPromptTemplateApi.CreateReq,
) {
  return requestClient.post<number>('/ai/prompt-template/create', data);
}

/** 启用某个版本 */
export function activatePromptTemplateVersion(id: number) {
  return requestClient.put<boolean>(`/ai/prompt-template/activate?id=${id}`);
}
