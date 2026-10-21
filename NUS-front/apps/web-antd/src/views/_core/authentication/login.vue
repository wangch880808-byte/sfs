<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable } from '@vben/hooks';
import { MdiWechat, SvgDingDingIcon } from '@vben/icons';
import { $t } from '@vben/locales';
import { loadScript } from '@vben/utils';

import { Spin } from 'ant-design-vue';

import {
  checkCaptcha,
  createWechatQrLogin,
  getCaptcha,
  getSocialLoginConfig,
  getWechatMiniProgramConfig,
  getWechatQrStatus,
  socialAuthRedirect,
} from '#/api/core/auth';
import { useAuthStore } from '#/store';

/** 登录过期弹窗复用本页面时，会通过插槽传入“重新登录”的标题等属性 */
interface Props {
  showRememberMe?: boolean;
  subTitle?: string;
  title?: string;
}

defineOptions({ name: 'Login' });

withDefaults(defineProps<Props>(), {
  showRememberMe: true,
  subTitle: '请使用学校分配的账号和密码登录',
  title: '欢迎登录师砺平台',
});

const { query } = useRoute();
const authStore = useAuthStore();

const captchaEnable = isCaptchaEnable();

const loginRef = ref();
const verifyRef = ref();

/** 钉钉和微信小程序入口均按后端配置显示 */
const showDingtalkLogin = ref(false);
const showWechatLogin = ref(false);
/** 只在至少一个可用渠道存在时显示第三方登录区域 */
const showThirdPartyLogin = computed(
  () => showDingtalkLogin.value || showWechatLogin.value,
);

onMounted(async () => {
  // 配置读不到时只隐藏对应入口，不弹全局错误提示
  const dingtalkConfigPromise = getSocialLoginConfig({ silentError: true })
    .then((config) => {
      showDingtalkLogin.value = config.dingtalkEnabled;
    })
    .catch(() => {
      showDingtalkLogin.value = false;
    });
  const wechatConfigPromise = getWechatMiniProgramConfig({ silentError: true })
    .then((config) => {
      showWechatLogin.value = config.enabled;
    })
    .catch(() => {
      showWechatLogin.value = false;
    });
  await Promise.all([dingtalkConfigPromise, wechatConfigPromise]);
});

const captchaType = 'blockPuzzle'; // 验证码类型：'blockPuzzle' | 'clickWord'

/** 处理登录 */
async function handleLogin(values: any) {
  // 如果开启验证码，则先验证验证码
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }
  // 无验证码，直接登录
  await authStore.authLogin('username', values);
}

/** 验证码通过，执行登录 */
async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    await authStore.authLogin('username', {
      ...(await loginRef.value.getFormApi().getValues()),
      captchaVerification,
    });
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

/** 处理第三方登录 */
const redirect = query?.redirect;
const SOCIAL_LOGIN_TYPE_CACHE_KEY = 'SOCIAL_LOGIN_TYPE';
const SOCIAL_LOGIN_REDIRECT_CACHE_KEY = 'SOCIAL_LOGIN_REDIRECT';
const loginMode = ref<'dingding' | 'password' | 'wechat'>('password');
const dingTalkQrLoading = ref(false);
const dingTalkQrError = ref('');
const dingTalkAuthorizeUrl = ref('');
const dingTalkQrHostRef = ref<HTMLDivElement>();
const dingTalkQrWidth = ref(320);
const dingTalkQrHeight = ref(351);
const dingTalkQrAuthorizing = ref(false);
const DINGTALK_QR_CONTAINER_ID = 'dingtalk_qr_login_element';
const DINGTALK_QR_WIDTH = 365;
const DINGTALK_QR_HEIGHT = 400;

declare global {
  interface Window {
    DDLogin?: (options: {
      goto: string;
      height: number;
      id: string;
      style?: string;
      width: number;
    }) => void;
  }
}

let dingTalkQrMessageHandler: ((event: MessageEvent) => void) | undefined;
let wechatQrPollTimer: number | undefined;
const wechatQrLoading = ref(false);
const wechatQrPolling = ref(false);
const wechatQrError = ref('');
const wechatQrState = ref<'CONFIRMED' | 'CONSUMED' | 'SCANNED' | 'WAITING'>(
  'WAITING',
);
const wechatQrImage = ref('');
const wechatQrTicket = ref('');
const wechatQrBrowserToken = ref('');
/** 正在显示小程序码图片：这时容器用白底，扫码才有足够对比 */
const wechatQrImageVisible = computed(
  () =>
    !wechatQrLoading.value &&
    !wechatQrError.value &&
    wechatQrState.value !== 'SCANNED' &&
    wechatQrState.value !== 'CONFIRMED' &&
    !!wechatQrImage.value,
);

function unbindDingTalkMessageListener() {
  if (!dingTalkQrMessageHandler) {
    return;
  }
  window.removeEventListener('message', dingTalkQrMessageHandler);
  dingTalkQrMessageHandler = undefined;
}

function resolveDingTalkLoginTmpCode(data: unknown) {
  if (!data) {
    return '';
  }
  if (typeof data === 'string') {
    const text = data.trim();
    if (!text) {
      return '';
    }
    if (text.startsWith('{') && text.endsWith('}')) {
      try {
        const parsed = JSON.parse(text) as Record<string, string>;
        return (
          parsed.loginTmpCode ||
          parsed.tmpCode ||
          parsed.authCode ||
          parsed.code ||
          ''
        );
      } catch {
        return '';
      }
    }
    return text;
  }
  if (typeof data === 'object') {
    const payload = data as Record<string, string | undefined>;
    return (
      payload.loginTmpCode ||
      payload.tmpCode ||
      payload.authCode ||
      payload.code ||
      ''
    );
  }
  return '';
}

function bindDingTalkMessageListener() {
  unbindDingTalkMessageListener();
  dingTalkQrMessageHandler = (event: MessageEvent) => {
    if (!event.origin.includes('login.dingtalk.com')) {
      return;
    }
    const loginTmpCode = resolveDingTalkLoginTmpCode(event.data);
    if (!loginTmpCode || !dingTalkAuthorizeUrl.value) {
      return;
    }
    try {
      dingTalkQrAuthorizing.value = true;
      const authorizeUrl = new URL(dingTalkAuthorizeUrl.value);
      authorizeUrl.searchParams.set('loginTmpCode', loginTmpCode);
      window.location.href = authorizeUrl.toString();
    } catch (error) {
      dingTalkQrAuthorizing.value = false;
      dingTalkQrError.value = `扫码后没能完成登录：${(error as Error)?.message || '请刷新二维码后重试'}`;
    }
  };
  window.addEventListener('message', dingTalkQrMessageHandler);
}

function measureDingTalkQrSize() {
  const containerWidth =
    dingTalkQrHostRef.value?.clientWidth ?? DINGTALK_QR_WIDTH;
  const width = Math.max(
    260,
    Math.min(DINGTALK_QR_WIDTH, Math.floor(containerWidth)),
  );
  const height = Math.round((DINGTALK_QR_HEIGHT / DINGTALK_QR_WIDTH) * width);
  dingTalkQrWidth.value = width;
  dingTalkQrHeight.value = height;
  return { height, width };
}

async function renderDingTalkQrCode(authorizeUrl: string) {
  await loadScript('https://g.alicdn.com/dingding/dinglogin/0.0.5/ddLogin.js');
  await nextTick();
  const target = document.querySelector<HTMLElement>(
    `#${DINGTALK_QR_CONTAINER_ID}`,
  );
  const DDLogin = window.DDLogin;
  if (!target || !DDLogin) {
    throw new Error('钉钉登录组件没有加载完整');
  }
  const { height, width } = measureDingTalkQrSize();
  DDLogin({
    id: DINGTALK_QR_CONTAINER_ID,
    goto: encodeURIComponent(authorizeUrl),
    // 二维码 iframe 透明，底色由外层的白色容器提供（扫码需要白底）
    style: 'border:none;background-color:transparent;',
    width,
    height,
  });
}

function buildSocialRedirectUri(type: number) {
  const query = new URLSearchParams({
    type: String(type),
    redirect: String(redirect || '/'),
  }).toString();
  if (import.meta.env.VITE_ROUTER_HISTORY === 'hash') {
    return `${location.origin}/#/auth/social-login?${query}`;
  }
  return `${location.origin}/auth/social-login?${query}`;
}

function buildDingTalkRedirectUri() {
  // hash 路由下，回调必须落到 hash 路由才能命中 SocialLogin 页面
  if (import.meta.env.VITE_ROUTER_HISTORY === 'hash') {
    return `${location.origin}/#/auth/social-login`;
  }
  return `${location.origin}/auth/social-login`;
}

function buildDingTalkGotoUrl(authorizeUrl: string) {
  try {
    const parsed = new URL(authorizeUrl);
    // 后端返回 qrconnect（用于生成二维码），前端 DDLogin 需要 sns_authorize（用于扫码确认后跳转）
    if (!parsed.pathname.includes('/connect/qrconnect')) {
      return authorizeUrl;
    }
    const goto = new URL(
      'https://oapi.dingtalk.com/connect/oauth2/sns_authorize',
    );
    const appid = parsed.searchParams.get('appid');
    const responseType = parsed.searchParams.get('response_type') || 'code';
    const scope = parsed.searchParams.get('scope') || 'snsapi_login';
    const state = parsed.searchParams.get('state') || '';
    const redirectUri = parsed.searchParams.get('redirect_uri') || '';
    if (appid) {
      goto.searchParams.set('appid', appid);
    }
    goto.searchParams.set('response_type', responseType);
    goto.searchParams.set('scope', scope);
    if (state) {
      goto.searchParams.set('state', state);
    }
    if (redirectUri) {
      goto.searchParams.set('redirect_uri', redirectUri);
    }
    return goto.toString();
  } catch {
    return authorizeUrl;
  }
}

function backToPasswordLogin() {
  loginMode.value = 'password';
  dingTalkQrAuthorizing.value = false;
  unbindDingTalkMessageListener();
  stopWechatQrPolling();
}

function stopWechatQrPolling() {
  if (wechatQrPollTimer !== undefined) {
    window.clearInterval(wechatQrPollTimer);
    wechatQrPollTimer = undefined;
  }
}

async function pollWechatQrStatus() {
  if (
    wechatQrPolling.value ||
    !wechatQrTicket.value ||
    !wechatQrBrowserToken.value
  ) {
    return;
  }
  wechatQrPolling.value = true;
  try {
    const status = await getWechatQrStatus({
      browserToken: wechatQrBrowserToken.value,
      ticket: wechatQrTicket.value,
    });
    wechatQrState.value = status.state;
    if (status.state === 'CONFIRMED') {
      stopWechatQrPolling();
      await authStore.authLogin('wechatQr', {
        browserToken: wechatQrBrowserToken.value,
        ticket: wechatQrTicket.value,
      });
    } else if (status.state === 'CONSUMED') {
      stopWechatQrPolling();
      wechatQrError.value = '这个二维码已经用过了，请刷新二维码后重新扫码';
    }
  } catch (error) {
    stopWechatQrPolling();
    wechatQrError.value =
      (error as Error)?.message || '二维码已失效，请刷新二维码后重新扫码';
  } finally {
    wechatQrPolling.value = false;
  }
}

async function openWechatQrLogin() {
  stopWechatQrPolling();
  wechatQrLoading.value = true;
  wechatQrError.value = '';
  wechatQrImage.value = '';
  wechatQrState.value = 'WAITING';
  loginMode.value = 'wechat';
  try {
    const result = await createWechatQrLogin();
    wechatQrImage.value = result.qrCodeDataUrl;
    wechatQrTicket.value = result.ticket;
    wechatQrBrowserToken.value = result.browserToken;
    wechatQrPollTimer = window.setInterval(pollWechatQrStatus, 1500);
  } catch (error) {
    wechatQrError.value =
      (error as Error)?.message || '小程序码没有加载出来，请稍后刷新重试';
  } finally {
    wechatQrLoading.value = false;
  }
}

async function openDingTalkQrLogin() {
  dingTalkQrLoading.value = true;
  dingTalkQrError.value = '';
  dingTalkQrAuthorizing.value = false;
  try {
    // 缓存社交登录上下文，兼容部分平台回调不透传自定义 query 参数
    sessionStorage.setItem(SOCIAL_LOGIN_TYPE_CACHE_KEY, '20');
    sessionStorage.setItem(
      SOCIAL_LOGIN_REDIRECT_CACHE_KEY,
      String(redirect || '/'),
    );

    const redirectUri = buildDingTalkRedirectUri();
    const authorizeUrl = await socialAuthRedirect(20, redirectUri);
    const dingTalkGotoUrl = buildDingTalkGotoUrl(authorizeUrl);
    dingTalkAuthorizeUrl.value = dingTalkGotoUrl;
    loginMode.value = 'dingding';
    await nextTick();
    dingTalkQrLoading.value = false;
    await nextTick();
    await renderDingTalkQrCode(dingTalkGotoUrl);
    bindDingTalkMessageListener();
  } catch (error) {
    dingTalkAuthorizeUrl.value = '';
    dingTalkQrError.value = `二维码没有加载出来：${(error as Error)?.message || '请稍后刷新重试'}`;
    console.error('钉钉二维码加载失败:', error);
  } finally {
    if (dingTalkQrLoading.value) {
      dingTalkQrLoading.value = false;
    }
  }
}

async function handleThirdLogin(type: number) {
  if (type <= 0) {
    return;
  }
  if (type === 20) {
    await openDingTalkQrLogin();
    return;
  }
  if (type === 50) {
    await openWechatQrLogin();
    return;
  }
  try {
    // 缓存社交登录上下文，兼容部分平台回调不透传自定义 query 参数
    sessionStorage.setItem(SOCIAL_LOGIN_TYPE_CACHE_KEY, String(type));
    sessionStorage.setItem(
      SOCIAL_LOGIN_REDIRECT_CACHE_KEY,
      String(redirect || '/'),
    );

    // 计算 redirectUri（标准 query 形式，避免 type%3D20 这类非标准参数）
    const redirectUri = buildSocialRedirectUri(type);

    // 进行跳转
    window.location.href = await socialAuthRedirect(type, redirectUri);
  } catch (error) {
    console.error('第三方登录处理失败:', error);
  }
}

onBeforeUnmount(() => {
  unbindDingTalkMessageListener();
  stopWechatQrPolling();
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.usernameTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_USERNAME),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.passwordTip'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.passwordTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_PASSWORD),
    },
  ];
});
</script>

<template>
  <div>
    <AuthenticationLogin
      v-if="loginMode === 'password'"
      ref="loginRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-forget-password="false"
      :show-qrcode-login="false"
      :show-register="false"
      :show-remember-me="showRememberMe"
      :show-third-party-login="showThirdPartyLogin"
      :sub-title="subTitle"
      :title="title"
      @submit="handleLogin"
      @third-login="handleThirdLogin"
    >
      <template #third-party-login>
        <!-- 平台扫码登录 -->
        <div v-if="showThirdPartyLogin" class="login-third">
          <div class="login-third__divider">
            <span class="login-third__line"></span>
            <span class="login-third__label">
              {{ $t('authentication.thirdPartyLogin') }}
            </span>
            <span class="login-third__line"></span>
          </div>
          <div class="login-third__methods">
            <button
              v-if="showDingtalkLogin"
              class="login-third__method"
              type="button"
              @click="handleThirdLogin(20)"
            >
              <SvgDingDingIcon class="size-5" />
              <span>钉钉扫码登录</span>
            </button>
            <button
              v-if="showWechatLogin"
              class="login-third__method"
              type="button"
              @click="handleThirdLogin(50)"
            >
              <MdiWechat class="login-third__wechat size-5" />
              <span>微信扫码登录</span>
            </button>
          </div>
        </div>
      </template>
    </AuthenticationLogin>
    <div v-else-if="loginMode === 'dingding'" class="login-qr">
      <h3 class="login-qr__title">钉钉扫码登录</h3>
      <p class="login-qr__desc">打开钉钉扫一扫，确认后自动登录</p>

      <div class="login-qr__box login-qr__box--dingtalk">
        <Spin v-if="dingTalkQrLoading" tip="正在加载二维码…" />
        <Spin v-else-if="dingTalkQrAuthorizing" tip="已扫码确认，正在登录…" />
        <div v-else-if="dingTalkQrError" class="login-qr__state">
          <p class="login-qr__error">{{ dingTalkQrError }}</p>
          <button
            class="login-qr__link"
            type="button"
            @click="openDingTalkQrLogin"
          >
            重新加载
          </button>
        </div>
        <div
          v-else-if="dingTalkAuthorizeUrl"
          ref="dingTalkQrHostRef"
          class="login-qr__frame"
          :style="{
            width: `${dingTalkQrWidth}px`,
            height: `${dingTalkQrHeight}px`,
          }"
        >
          <div
            id="dingtalk_qr_login_element"
            class="h-full w-full overflow-hidden"
          ></div>
        </div>
        <p v-else class="login-qr__hint">
          二维码还没有加载，点「刷新二维码」再试。
        </p>
      </div>

      <div class="login-qr__actions">
        <button
          class="login-qr__link"
          type="button"
          @click="openDingTalkQrLogin"
        >
          刷新二维码
        </button>
        <button
          class="login-qr__link is-muted"
          type="button"
          @click="backToPasswordLogin"
        >
          返回账号密码登录
        </button>
      </div>
    </div>
    <div v-else class="login-qr">
      <h3 class="login-qr__title">微信小程序扫码登录</h3>
      <p class="login-qr__desc">用微信扫一扫，进入小程序后确认登录</p>

      <div
        :class="{ 'is-qr': wechatQrImageVisible }"
        class="login-qr__box login-qr__box--wechat"
      >
        <Spin v-if="wechatQrLoading" tip="正在加载小程序码…" />
        <div v-else-if="wechatQrError" class="login-qr__state">
          <p class="login-qr__error">{{ wechatQrError }}</p>
        </div>
        <div v-else-if="wechatQrState === 'SCANNED'" class="login-qr__state">
          <MdiWechat class="login-third__wechat mx-auto size-12" />
          <p class="login-qr__state-title">已扫码</p>
          <p class="login-qr__hint">请在小程序里确认登录</p>
        </div>
        <Spin
          v-else-if="wechatQrState === 'CONFIRMED'"
          tip="已确认，正在登录…"
        />
        <img
          v-else-if="wechatQrImage"
          :src="wechatQrImage"
          alt="微信小程序登录码"
          class="size-full object-contain"
        />
      </div>

      <div class="login-qr__actions">
        <button class="login-qr__link" type="button" @click="openWechatQrLogin">
          刷新二维码
        </button>
        <button
          class="login-qr__link is-muted"
          type="button"
          @click="backToPasswordLogin"
        >
          返回账号密码登录
        </button>
      </div>
    </div>
    <Verification
      ref="verifyRef"
      v-if="captchaEnable && loginMode === 'password'"
      :captcha-type="captchaType"
      :check-captcha-api="checkCaptcha"
      :get-captcha-api="getCaptcha"
      :img-size="{ width: '400px', height: '200px' }"
      mode="pop"
      @on-success="handleVerifySuccess"
    />
  </div>
</template>

<style scoped>
/* 登录页内容（V1.0.1.6 界面规范）：颜色取 token；二维码区域保持白底，扫码需要足够对比 */
.login-third {
  width: 100%;
  margin-top: var(--nus-space-6);
}

.login-third__divider {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
}

.login-third__line {
  flex: 1;
  border-bottom: 1px solid var(--nus-border-subtle);
}

.login-third__label {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.login-third__methods {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
  margin-top: var(--nus-space-4);
}

.login-third__method {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 36px;
  font-size: var(--nus-fs-body);
  color: var(--nus-text);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--nus-border);
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.login-third__method:hover {
  background: var(--nus-bg-hover);
  border-color: var(--nus-border-strong);
}

.login-third__method:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 1px;
}

.login-third__wechat {
  color: var(--nus-success);
}

.login-qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 460px;
  padding: 0 var(--nus-space-6);
  text-align: center;
}

.login-qr__title {
  margin: 0;
  font-size: var(--nus-fs-title);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-title);
  color: var(--nus-text);
}

.login-qr__desc {
  margin: var(--nus-space-2) 0 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
}

.login-qr__box {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--nus-space-6);
  border: 1px dashed var(--nus-border);
  border-radius: var(--nus-radius-lg);
}

.login-qr__box--dingtalk {
  width: 100%;
  max-width: 365px;
  min-height: 280px;
  padding: var(--nus-space-4);
  background: var(--nus-bg-sunken);
}

/* 显示小程序码时容器用白底（二维码需要白底才能扫），其余状态用凹陷底 */
.login-qr__box--wechat {
  width: 280px;
  height: 280px;
  padding: var(--nus-space-4);
  background: var(--nus-bg-sunken);
}

.login-qr__box--wechat.is-qr {
  background: white;
  border-color: var(--nus-border-subtle);
  border-style: solid;
}

.login-qr__frame {
  overflow: hidden;
  background: white;
  border-radius: var(--nus-radius);
}

.login-qr__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 var(--nus-space-4);
}

.login-qr__state-title {
  margin: var(--nus-space-4) 0 0;
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
}

.login-qr__hint {
  margin: var(--nus-space-2) 0 0;
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-tertiary);
}

.login-qr__error {
  margin: 0;
  font-size: var(--nus-fs-dense);
  color: var(--nus-danger);
}

.login-qr__actions {
  display: flex;
  gap: var(--nus-space-4);
  align-items: center;
  margin-top: var(--nus-space-6);
}

.login-qr__link {
  padding: 0;
  font-size: var(--nus-fs-body);
  color: var(--nus-primary);
  cursor: pointer;
  background: transparent;
  border: 0;
  transition: var(--nus-transition-colors);
}

.login-qr__link:hover {
  color: var(--nus-primary-hover);
  text-decoration: underline;
}

.login-qr__link.is-muted {
  color: var(--nus-text-secondary);
}

.login-qr__link.is-muted:hover {
  color: var(--nus-text);
}
</style>
