/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';
import { createApiEncrypt } from '@vben/utils';

import { message } from 'ant-design-vue';

import { PASSWORD_CHANGE_PATH } from '#/router/password-change';
import { useAuthStore } from '#/store';
import {
  createErrorToastDeduper,
  getGlobalErrorKey,
} from '#/utils/error-toast';

import { refreshTokenApi } from './core';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const apiEncrypt = createApiEncrypt(import.meta.env);
const IDEMPOTENCY_CACHE_TTL = 2 * 60 * 1000;
const IDEMPOTENCY_FINGERPRINT_KEY = '__idempotencyFingerprint';
const IDEMPOTENCY_HEADER = 'X-Idempotency-Key';
const MUTATION_METHODS = new Set(['DELETE', 'PATCH', 'POST', 'PUT']);
// 与后端 system 模块 AUTH_PASSWORD_CHANGE_REQUIRED 保持一致
const PASSWORD_CHANGE_REQUIRED_CODE = 1_002_000_009;
/** 同一条全局错误提示几秒内只弹一次（断网、后端重启时多个请求同时失败） */
const shouldShowGlobalError = createErrorToastDeduper();
const mutationRequestCache = new Map<
  string,
  {
    expireAt: number;
    key: string;
  }
>();

function cleanupMutationRequestCache() {
  const now = Date.now();
  mutationRequestCache.forEach((value, key) => {
    if (value.expireAt <= now) {
      mutationRequestCache.delete(key);
    }
  });
}

function normalizeMethod(method?: string) {
  return String(method ?? 'GET').toUpperCase();
}

function isMutationMethod(method?: string) {
  return MUTATION_METHODS.has(normalizeMethod(method));
}

function generateIdempotencyKey() {
  if (
    typeof crypto !== 'undefined' &&
    typeof crypto.randomUUID === 'function'
  ) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function serializeFormDataValue(value: FormDataEntryValue) {
  if (value instanceof File) {
    return {
      lastModified: value.lastModified,
      name: value.name,
      size: value.size,
      type: value.type,
    };
  }
  return value;
}

function serializeRequestPayload(payload: unknown): string {
  if (payload === null || payload === undefined) {
    return '';
  }
  if (payload instanceof FormData) {
    const entries: Array<[string, FormDataEntryValue | Record<string, any>]> =
      [];
    payload.forEach((value, key) => {
      entries.push([key, serializeFormDataValue(value)]);
    });
    return JSON.stringify(entries);
  }
  if (payload instanceof URLSearchParams) {
    return payload.toString();
  }
  if (typeof payload === 'string') {
    return payload;
  }
  try {
    return JSON.stringify(payload);
  } catch {
    return String(payload);
  }
}

function buildMutationFingerprint(config: Record<string, any>) {
  return [
    normalizeMethod(config.method),
    String(config.baseURL ?? ''),
    String(config.url ?? ''),
    serializeRequestPayload(config.params),
    serializeRequestPayload(config.data),
  ].join('::');
}

function getMutationFingerprint(config: Record<string, any>) {
  return String(config?.[IDEMPOTENCY_FINGERPRINT_KEY] ?? '');
}

function getOrCreateMutationIdempotency(config: Record<string, any>) {
  cleanupMutationRequestCache();
  const fingerprint = buildMutationFingerprint(config);
  const now = Date.now();
  const cached = mutationRequestCache.get(fingerprint);
  if (cached && cached.expireAt > now) {
    return { fingerprint, key: cached.key };
  }
  const key = generateIdempotencyKey();
  mutationRequestCache.set(fingerprint, {
    expireAt: now + IDEMPOTENCY_CACHE_TTL,
    key,
  });
  return { fingerprint, key };
}

function clearMutationIdempotency(config?: Record<string, any>) {
  const fingerprint = getMutationFingerprint(config ?? {});
  if (!fingerprint) {
    return;
  }
  mutationRequestCache.delete(fingerprint);
}

function shouldRetainMutationIdempotency(error: any) {
  if (error?.code === 'ERR_CANCELED') {
    return false;
  }
  const err = String(error?.toString?.() ?? '');
  return err.includes('Network Error') || error?.message?.includes?.('timeout');
}

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });

  /**
   * 重新认证逻辑
   */
  async function doReAuthenticate() {
    console.warn('Access token or refresh token is invalid or expired. ');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    accessStore.setAccessToken(null);
    if (
      preferences.app.loginExpiredMode === 'modal' &&
      accessStore.isAccessChecked
    ) {
      accessStore.setLoginExpired(true);
    } else {
      await authStore.logout();
    }
  }

  /**
   * 刷新token逻辑
   */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const refreshToken = accessStore.refreshToken as string;
    if (!refreshToken) {
      throw new Error('Refresh token is null!');
    }
    const resp = await refreshTokenApi(refreshToken);
    const newToken = resp?.data?.data?.accessToken;
    // add by 芋艿：这里一定要抛出 resp.data，从而触发 authenticateResponseInterceptor 中，刷新令牌失败！！！
    if (!newToken) {
      throw resp.data;
    }
    accessStore.setAccessToken(newToken);
    return newToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers = config.headers ?? {};
      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;
      if (isMutationMethod(config.method)) {
        const { fingerprint, key } = getOrCreateMutationIdempotency(
          config as Record<string, any>,
        );
        (config as Record<string, any>)[IDEMPOTENCY_FINGERPRINT_KEY] =
          fingerprint;
        config.headers[IDEMPOTENCY_HEADER] = key;
      }

      // 是否 API 加密
      if ((config.headers || {}).isEncrypt) {
        try {
          // 加密请求数据
          if (config.data) {
            config.data = apiEncrypt.encryptRequest(config.data);
            // 设置加密标识头
            config.headers[apiEncrypt.getEncryptHeader()] = 'true';
          }
        } catch (error) {
          console.error('请求数据加密失败:', error);
          throw error;
        }
      }
      return config;
    },
  });

  client.addResponseInterceptor({
    fulfilled: (response) => {
      clearMutationIdempotency(response?.config as Record<string, any>);
      return response;
    },
    rejected: (error) => {
      if (!shouldRetainMutationIdempotency(error)) {
        clearMutationIdempotency(
          (error?.config ?? error?.response?.config) as Record<string, any>,
        );
      }
      return Promise.reject(error);
    },
  });

  // API 解密响应拦截器
  client.addResponseInterceptor({
    fulfilled: (response) => {
      // 检查是否需要解密响应数据
      const encryptHeader = apiEncrypt.getEncryptHeader();
      const isEncryptResponse =
        response.headers[encryptHeader] === 'true' ||
        response.headers[encryptHeader.toLowerCase()] === 'true';
      if (isEncryptResponse && typeof response.data === 'string') {
        try {
          // 解密响应数据
          response.data = apiEncrypt.decryptResponse(response.data);
        } catch (error) {
          console.error('响应数据解密失败:', error);
          throw new Error(`响应数据解密失败：${(error as Error).message}`);
        }
      }
      return response;
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 这里可以根据业务进行定制,你可以拿到 error 内的信息进行定制化处理，根据不同的 code 做不同的提示，而不是直接使用 message.error 提示 msg
      // 当前mock接口返回的错误字段是 error 或者 message
      const responseData = error?.response?.data ?? {};
      if (
        Number(error?.data?.code ?? responseData?.code) ===
        PASSWORD_CHANGE_REQUIRED_CODE
      ) {
        const authStore = useAuthStore();
        authStore.setPasswordChangeRequired(true);
        void import('#/router').then(({ router }) => {
          if (router.currentRoute.value.path !== PASSWORD_CHANGE_PATH) {
            return router.replace(PASSWORD_CHANGE_PATH);
          }
        });
        return;
      }
      const errorMessage =
        responseData?.error ?? responseData?.message ?? responseData.msg ?? '';
      // add by 芋艿：特殊：避免 401 “账号未登录”，重复提示。因为，此时会跳转到登录界面，只需提示一次！！！
      if (error?.data?.code === 401) {
        return;
      }
      if (error?.config?.silentError) {
        return;
      }
      // 如果没有错误信息，则会根据状态码进行提示；同一条提示几秒内只弹一次
      const text = String(errorMessage || msg || '');
      if (!shouldShowGlobalError(text)) {
        return;
      }
      message.error({ content: text, key: getGlobalErrorKey(text) });
    }),
  );

  return client;
}

/**
 * 批量写入、封存、导出等可能超过默认 30 秒的请求使用的超时。
 * 前端超时后后端仍会继续执行，所以这类操作宁可多等，避免用户以为失败后重复提交。
 */
export const LONG_REQUEST_TIMEOUT = 120_000;

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });
baseRequestClient.addRequestInterceptor({
  fulfilled: (config) => {
    return config;
  },
});
