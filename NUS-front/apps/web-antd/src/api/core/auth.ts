import type { AuthPermissionInfo } from '@vben/types';

import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
    captchaVerification?: string;
    // 绑定社交登录时，需要传递如下参数
    socialType?: number;
    socialCode?: string;
    socialState?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
    refreshToken: string;
    userId: number;
    expiresTime: number;
    passwordChangeRequired: boolean;
  }

  export interface SocialLoginConfig {
    dingtalkEnabled: boolean;
  }

  /** 社交快捷登录接口参数 */
  export interface SocialLoginParams {
    type: number;
    code: string;
    state: string;
  }

  export interface WechatQrCreateResult {
    ticket: string;
    browserToken: string;
    purpose: 'BIND' | 'LOGIN';
    qrCodeDataUrl: string;
    expiresAt: string;
  }

  export interface WechatMiniProgramConfig {
    enabled: boolean;
  }

  export interface WechatQrStatusResult {
    state: 'CONFIRMED' | 'CONSUMED' | 'SCANNED' | 'WAITING';
    purpose: 'BIND' | 'LOGIN';
    accountNickname?: string;
    expiresAt: string;
  }

  export interface WechatQrLoginParams {
    ticket: string;
    browserToken: string;
  }
}

/** 登录 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/system/auth/login', data, {
    headers: {
      isEncrypt: false,
    },
  });
}

/** 刷新 accessToken */
export async function refreshTokenApi(refreshToken: string) {
  return baseRequestClient.post(
    `/system/auth/refresh-token?refreshToken=${refreshToken}`,
  );
}

/** 退出登录 */
export async function logoutApi(accessToken: string) {
  return baseRequestClient.post(
    '/system/auth/logout',
    {},
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );
}

/** 获取权限信息 */
export async function getAuthPermissionInfoApi() {
  return requestClient.get<AuthPermissionInfo>(
    '/system/auth/get-permission-info',
  );
}

/** 获取验证码 */
export async function getCaptcha(data: any) {
  return baseRequestClient.post('/system/captcha/get', data);
}

/** 校验验证码 */
export async function checkCaptcha(data: any) {
  return baseRequestClient.post('/system/captcha/check', data);
}

/** 获得管理后台社交登录公开配置（登录页读不到时只隐藏入口，传 silentError: true 不弹全局提示） */
export function getSocialLoginConfig(options?: { silentError?: boolean }) {
  return requestClient.get<AuthApi.SocialLoginConfig>(
    '/system/auth/social-login-config',
    options,
  );
}

/** 社交授权的跳转 */
export async function socialAuthRedirect(type: number, redirectUri: string) {
  return requestClient.get('/system/auth/social-auth-redirect', {
    params: {
      type,
      redirectUri,
    },
  });
}

/** 社交快捷登录 */
export async function socialLogin(data: AuthApi.SocialLoginParams) {
  return requestClient.post<AuthApi.LoginResult>(
    '/system/auth/social-login',
    data,
  );
}

/** 获得微信小程序登录公开配置（登录页读不到时只隐藏入口，传 silentError: true 不弹全局提示） */
export function getWechatMiniProgramConfig(options?: {
  silentError?: boolean;
}) {
  return requestClient.get<AuthApi.WechatMiniProgramConfig>(
    '/system/wechat-mini-program/config',
    options,
  );
}

/** 创建微信小程序网页登录码 */
export function createWechatQrLogin() {
  return requestClient.post<AuthApi.WechatQrCreateResult>(
    '/system/wechat-mini-program/qr/login',
  );
}

/** 查询微信扫码状态 */
export function getWechatQrStatus(params: AuthApi.WechatQrLoginParams) {
  return requestClient.get<AuthApi.WechatQrStatusResult>(
    '/system/wechat-mini-program/qr/status',
    { params },
  );
}

/** 使用已确认的二维码换取现有平台 OAuth2 令牌 */
export function wechatQrLogin(data: AuthApi.WechatQrLoginParams) {
  return requestClient.post<AuthApi.LoginResult>(
    '/system/wechat-mini-program/qr/exchange',
    data,
  );
}
