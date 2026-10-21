<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { getUrlValue } from '@vben/utils';

import { Alert, Spin } from 'ant-design-vue';

import { checkCaptcha, getCaptcha } from '#/api/core/auth';
import { useAuthStore } from '#/store';

import { shouldShowSocialBindForm } from './social-login-state';

defineOptions({ name: 'SocialLogin' });

const authStore = useAuthStore();
const { query } = useRoute();
const captchaEnable = isCaptchaEnable();
const SOCIAL_LOGIN_TYPE_CACHE_KEY = 'SOCIAL_LOGIN_TYPE';
const SOCIAL_LOGIN_REDIRECT_CACHE_KEY = 'SOCIAL_LOGIN_REDIRECT';

const loginRef = ref();
const verifyRef = ref();

const captchaType = 'blockPuzzle'; // 验证码类型：'blockPuzzle' | 'clickWord'

/** 尝试登录：当账号已经绑定，socialLogin 会直接获得 token */
const cachedSocialType = Number(
  sessionStorage.getItem(SOCIAL_LOGIN_TYPE_CACHE_KEY) || '',
);
const socialType = Number(getUrlValue('type') || cachedSocialType || 0);
const socialCode = (query?.code as string) || getUrlValue('code');
const socialState = (query?.state as string) || getUrlValue('state');
const hasValidSocialType = socialType > 0;
const processingSocialLogin = ref(!!socialCode);
const showBindForm = ref(false);
const socialLoginError = ref('');
const socialPayload = hasValidSocialType
  ? {
      socialType,
      socialCode,
      socialState,
    }
  : {};
function clearSocialLoginCache() {
  sessionStorage.removeItem(SOCIAL_LOGIN_TYPE_CACHE_KEY);
  sessionStorage.removeItem(SOCIAL_LOGIN_REDIRECT_CACHE_KEY);
}
async function tryLogin() {
  // 尝试登录
  await authStore.authLogin('social', {
    type: socialType,
    code: socialCode,
    state: socialState,
  });
  clearSocialLoginCache();
}

/** 处理登录 */
async function handleLogin(values: any) {
  // 如果开启验证码，则先验证验证码
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }

  // 无验证码，直接登录
  await authStore.authLogin('username', {
    ...values,
    ...socialPayload,
  });
  clearSocialLoginCache();
}

/** 验证码通过，执行登录 */
async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    await authStore.authLogin('username', {
      ...(await loginRef.value.getFormApi().getValues()),
      captchaVerification,
      ...socialPayload,
    });
    clearSocialLoginCache();
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

onMounted(async () => {
  // 只有在三方回调携带 code 时，才尝试三方快捷登录
  if (!socialCode) {
    socialLoginError.value = '登录信息不完整，请返回登录页重新扫码登录';
    return;
  }
  processingSocialLogin.value = true;
  showBindForm.value = false;
  socialLoginError.value = '';
  // 回调缺少 type 时，提示重新发起，避免 socialType=0 触发后端枚举校验报错
  if (!hasValidSocialType) {
    processingSocialLogin.value = false;
    socialLoginError.value = '登录信息不完整，请返回登录页重新扫码登录';
    return;
  }
  try {
    await tryLogin();
  } catch (error) {
    processingSocialLogin.value = false;
    showBindForm.value = shouldShowSocialBindForm(error);
    if (!showBindForm.value) {
      const response = error as {
        data?: { msg?: string };
        response?: { data?: { msg?: string } };
      };
      socialLoginError.value =
        response?.response?.data?.msg ??
        response?.data?.msg ??
        '第三方登录没有成功，请返回登录页重新扫码';
    }
  }
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
    <div
      v-if="processingSocialLogin"
      class="flex min-h-[420px] flex-col items-center justify-center px-6 text-center"
    >
      <Spin size="large" />
      <p class="mt-6 text-base font-medium text-foreground">正在用钉钉登录…</p>
      <p class="mt-2 text-sm text-muted-foreground">
        已扫码确认，正在校验登录状态并进入系统
      </p>
    </div>
    <div v-else-if="showBindForm" class="space-y-4">
      <Alert
        type="warning"
        show-icon
        banner
        message="钉钉账号未绑定平台账号"
        description="请先输入平台账号和密码完成绑定；绑定成功后，下次扫码可直接登录。"
      />
      <AuthenticationLogin
        ref="loginRef"
        :form-schema="formSchema"
        :loading="authStore.loginLoading"
        :show-code-login="false"
        :show-forget-password="false"
        :show-qrcode-login="false"
        :show-third-party-login="false"
        :show-register="false"
        @submit="handleLogin"
      />
    </div>
    <div v-else class="space-y-4 px-6 py-12 text-center">
      <Alert type="error" show-icon :message="socialLoginError" />
      <router-link class="text-primary" to="/auth/login">
        返回登录页
      </router-link>
    </div>
    <Verification
      ref="verifyRef"
      v-if="captchaEnable && showBindForm"
      :captcha-type="captchaType"
      :check-captcha-api="checkCaptcha"
      :get-captcha-api="getCaptcha"
      :img-size="{ width: '400px', height: '200px' }"
      mode="pop"
      @on-success="handleVerifySuccess"
    />
  </div>
</template>
