import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace AiModelModelApi {
  export interface Model {
    id: number; // 编号
    keyId: number; // API 秘钥编号
    name: string; // 模型名字
    model: string; // 模型标识
    platform: string; // 模型平台
    type: number; // 模型类型
    sort: number; // 排序
    status: number; // 状态
    temperature?: number; // 温度参数
    maxTokens?: number; // 单条回复的最大 Token 数量
    maxContexts?: number; // 上下文的最大 Message 数量
  }

  export interface BusinessModelBinding {
    businessCode: string;
    businessName: string;
    modelId?: number;
    modelName?: string;
    available: boolean;
  }
}

/** 查询模型分页 */
export function getModelPage(params: PageParam) {
  return requestClient.get<PageResult<AiModelModelApi.Model>>(
    '/ai/model/page',
    { params },
  );
}

/** 获得模型列表 */
export function getModelSimpleList(type?: number) {
  return requestClient.get<AiModelModelApi.Model[]>('/ai/model/simple-list', {
    params: {
      type,
    },
  });
}

/** 查询模型详情 */
export function getModel(id: number) {
  return requestClient.get<AiModelModelApi.Model>(`/ai/model/get?id=${id}`);
}

/** 新增模型 */
export function createModel(data: AiModelModelApi.Model) {
  return requestClient.post('/ai/model/create', data);
}

/** 修改模型 */
export function updateModel(data: AiModelModelApi.Model) {
  return requestClient.put('/ai/model/update', data);
}

/** 删除模型 */
export function deleteModel(id: number) {
  return requestClient.delete(`/ai/model/delete?id=${id}`);
}

export function getBusinessModelBindings() {
  return requestClient.get<AiModelModelApi.BusinessModelBinding[]>(
    '/ai/business-model-binding/list',
  );
}

export function saveBusinessModelBinding(
  businessCode: string,
  modelId: number,
) {
  return requestClient.put('/ai/business-model-binding/save', {
    businessCode,
    modelId,
  });
}

/** 一次保存全部业务绑定，由服务端事务保证一致性。 */
export function saveBusinessModelBindings(
  bindings: Array<{ businessCode: string; modelId: number }>,
) {
  return requestClient.put('/ai/business-model-binding/save-batch', {
    bindings,
  });
}

/** 从服务器调用已保存的模型，不在浏览器中接触密钥。 */
export function testModelConnection(id: number) {
  return requestClient.post<{
    durationMs: number;
    message: string;
    success: boolean;
  }>('/ai/model/test', undefined, { params: { id }, timeout: 70_000 });
}
