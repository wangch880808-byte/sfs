import type { PageParam, PageResult } from '@vben/request';

import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace SystemUserApi {
  /** 用户扩展信息 */
  export interface UserProfile {
    professionalTitle?: number;
    gradeYear?: number;
    classIds?: number[];
  }

  /** 用户信息 */
  export interface User {
    id?: number;
    username: string;
    nickname: string;
    userType?: number;
    lockUserType?: boolean;
    deptId: number;
    postIds?: number[];
    roleIds?: number[];
    profile?: UserProfile;
    email: string;
    mobile: string;
    sex: number;
    avatar: string;
    loginIp: string;
    status: number;
    remark: string;
    professionalTitle?: number;
    classNames?: string;
    createTime?: Date;
  }
}

/** 查询用户管理列表 */
export function getUserPage(params: PageParam) {
  return requestClient.get<PageResult<SystemUserApi.User>>(
    '/system/user/page',
    { params },
  );
}

/** 查询用户详情 */
export function getUser(id: number) {
  return requestClient.get<SystemUserApi.User>(`/system/user/get?id=${id}`);
}

/** 新增用户 */
export function createUser(data: SystemUserApi.User) {
  return requestClient.post('/system/user/create', data);
}

/** 从教师专用入口新增用户，服务端会强制合并教师角色 */
export function createTeacher(data: SystemUserApi.User) {
  return requestClient.post('/system/user/create-teacher', data);
}

/** 从学生专用入口新增用户，服务端会强制合并学生角色 */
export function createStudent(data: SystemUserApi.User) {
  return requestClient.post('/system/user/create-student', data);
}

/** 修改用户 */
export function updateUser(data: SystemUserApi.User) {
  return requestClient.put('/system/user/update', data);
}

/** 删除用户 */
export function deleteUser(id: number) {
  return requestClient.delete(`/system/user/delete?id=${id}`);
}

/** 批量删除用户 */
export function deleteUserList(ids: number[]) {
  return requestClient.delete(`/system/user/delete-list?ids=${ids.join(',')}`);
}

/** 导出用户 */
export function exportUser(params: any) {
  return requestClient.download('/system/user/export-excel', {
    params,
    timeout: LONG_REQUEST_TIMEOUT,
  });
}

/** 用户密码重置 */
export function resetUserPassword(id: number, password: string) {
  return requestClient.put('/system/user/update-password', { id, password });
}

/** 用户状态修改 */
export function updateUserStatus(id: number, status: number) {
  return requestClient.put('/system/user/update-status', { id, status });
}

/** 获取用户精简信息列表 */
export function getSimpleUserList(params?: { userType?: number }) {
  return requestClient.get<SystemUserApi.User[]>('/system/user/simple-list', {
    params,
  });
}
