import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace EduTermApi {
  /** 学期信息 */
  export interface Term {
    id?: number;
    organizationId: number;
    code: string;
    name: string;
    startsOn: string;
    endsOn: string;
    status?: number;
    createTime?: Date;
  }

  /** 学期分页查询参数 */
  export interface TermPageReq extends PageParam {
    name?: string;
    code?: string;
    organizationId?: number;
    status?: number;
  }
}

/** 查询学期分页 */
export function getTermPage(params: EduTermApi.TermPageReq) {
  return requestClient.get<PageResult<EduTermApi.Term>>('/edu/term/page', {
    params,
  });
}

/** 查询学期详情 */
export function getTerm(id: number) {
  return requestClient.get<EduTermApi.Term>(`/edu/term/get?id=${id}`);
}

/** 新增学期 */
export function createTerm(data: EduTermApi.Term) {
  return requestClient.post('/edu/term/create', data);
}

/** 修改学期 */
export function updateTerm(data: EduTermApi.Term) {
  return requestClient.put('/edu/term/update', data);
}

/** 归档学期 */
export function archiveTerm(id: number) {
  return requestClient.put(`/edu/term/archive?id=${id}`);
}

/** 启用学期 */
export function enableTerm(id: number) {
  return requestClient.put(`/edu/term/enable?id=${id}`);
}
