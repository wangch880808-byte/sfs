import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace SystemSocialUserApi {
  /** 社交用户信息 */
  export interface SocialUser {
    id?: number;
    type: number;
    openid: string;
    userId?: number;
    userAccount?: string;
    userNickname?: string;
    accountUserType?: number;
    token: string;
    rawTokenInfo: string;
    nickname: string;
    avatar: string;
    rawUserInfo: string;
    code: string;
    state: string;
    createTime?: Date;
    updateTime?: Date;
  }

  /** 社交绑定请求 */
  export interface SocialUserBindReqVO {
    type: number;
    code: string;
    state: string;
  }

  /** 取消社交绑定请求 */
  export interface SocialUserUnbindReqVO {
    type: number;
    openid: string;
  }

  export interface WechatBinding {
    bound: boolean;
    openidMasked?: string;
  }

  export interface WechatBindQr {
    ticket: string;
    browserToken: string;
    purpose: 'BIND';
    qrCodeDataUrl: string;
    expiresAt: string;
  }

  export interface WechatQrStatus {
    state: 'CONFIRMED' | 'SCANNED' | 'WAITING';
    purpose: 'BIND';
    accountNickname?: string;
    expiresAt: string;
  }
}

/** 查询社交用户列表 */
export function getSocialUserPage(params: PageParam) {
  return requestClient.get<PageResult<SystemSocialUserApi.SocialUser>>(
    '/system/social-user/page',
    { params },
  );
}

/** 查询社交用户详情 */
export function getSocialUser(id: number) {
  return requestClient.get<SystemSocialUserApi.SocialUser>(
    `/system/social-user/get?id=${id}`,
  );
}

/** 删除社交用户并解除绑定 */
export function deleteSocialUser(id: number) {
  return requestClient.delete<boolean>(`/system/social-user/delete?id=${id}`);
}

/** 社交绑定，使用 code 授权码 */
export function socialBind(data: SystemSocialUserApi.SocialUserBindReqVO) {
  return requestClient.post<boolean>('/system/social-user/bind', data);
}

/** 取消社交绑定 */
export function socialUnbind(data: SystemSocialUserApi.SocialUserUnbindReqVO) {
  return requestClient.delete<boolean>('/system/social-user/unbind', { data });
}

/** 获得绑定社交用户列表 */
export function getBindSocialUserList() {
  return requestClient.get<SystemSocialUserApi.SocialUser[]>(
    '/system/social-user/get-bind-list',
  );
}

/** 查询当前账号的微信小程序绑定状态 */
export function getWechatBinding() {
  return requestClient.get<SystemSocialUserApi.WechatBinding>(
    '/system/wechat-mini-program/binding',
  );
}

/** 创建当前账号的微信小程序绑定码 */
export function createWechatBindQr() {
  return requestClient.post<SystemSocialUserApi.WechatBindQr>(
    '/system/wechat-mini-program/qr/bind',
  );
}

/** 查询微信小程序绑定码状态 */
export function getWechatBindQrStatus(params: {
  browserToken: string;
  ticket: string;
}) {
  return requestClient.get<SystemSocialUserApi.WechatQrStatus>(
    '/system/wechat-mini-program/qr/status',
    { params },
  );
}

/** 解绑当前账号的微信小程序 */
export function unbindWechat() {
  return requestClient.delete<boolean>('/system/wechat-mini-program/binding');
}
