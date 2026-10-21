import type { PageParam, PageResult } from '@vben/request';

import type { SystemUserApi } from '#/api/system/user';

import { requestClient } from '#/api/request';

export namespace EduUserApi {
  export interface UserPageReqVO extends PageParam {
    userType: 1 | 2;
    includeArchived?: boolean;
    status?: number;
  }
}

/** 教务侧教师、学生账号分页 */
export function getEduUserPage(params: EduUserApi.UserPageReqVO) {
  return requestClient.get<PageResult<SystemUserApi.User>>('/edu/user/page', {
    params,
  });
}

/** 停用教务账号 */
export function disableEduUser(id: number) {
  return requestClient.delete(`/edu/user/delete?id=${id}`);
}

/** 批量停用教务账号 */
export function disableEduUserList(ids: number[]) {
  return requestClient.delete(`/edu/user/delete-list?ids=${ids.join(',')}`);
}

/** 恢复教务账号 */
export function restoreEduUser(id: number) {
  return requestClient.put(`/edu/user/restore?id=${id}`);
}

/** 批量恢复教务账号 */
export function restoreEduUserList(ids: number[]) {
  return requestClient.put(`/edu/user/restore-list?ids=${ids.join(',')}`);
}
