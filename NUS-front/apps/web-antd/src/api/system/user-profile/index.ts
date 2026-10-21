import { requestClient } from '#/api/request';

export namespace SystemUserProfileExtApi {
  /** 用户扩展信息（精简） */
  export interface UserProfileSimple {
    id?: number;
    userId: number;
    userType?: number;
    professionalTitle?: number;
    gradeYear?: number;
    classIds?: number[];
  }
}

/** 获取用户扩展信息精简列表 */
export function getSimpleUserProfileList(params?: { userType?: number }) {
  return requestClient.get<SystemUserProfileExtApi.UserProfileSimple[]>(
    '/system/user-profile/simple-list',
    { params },
  );
}
