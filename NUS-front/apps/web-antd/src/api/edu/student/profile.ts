import { requestClient } from '#/api/request';

export namespace StudentProfileApi {
  export interface ProfileInfo {
    userId: number;
    username: string;
    nickname: string;
    mobile: string;
    email: string;
    avatar: string;
    deptId?: number;
    deptName: string;
    majorName: string;
    gradeYear?: number;
    classIds: number[];
    classNames: string[];
    className: string;
  }

  export interface UpdateProfileReqVO {
    nickname?: string;
    mobile?: string;
    email?: string;
    avatar?: string;
  }

  export interface UpdatePasswordReqVO {
    oldPassword: string;
    newPassword: string;
  }
}

/** 获取个人信息 */
export function getProfile() {
  return requestClient.get<StudentProfileApi.ProfileInfo>(
    '/edu/student/profile/get',
  );
}

/** 更新个人信息 */
export function updateProfile(data: StudentProfileApi.UpdateProfileReqVO) {
  return requestClient.put('/edu/student/profile/update', data);
}

/** 修改密码 */
export function updatePassword(data: StudentProfileApi.UpdatePasswordReqVO) {
  return requestClient.put('/edu/student/profile/password', data);
}
