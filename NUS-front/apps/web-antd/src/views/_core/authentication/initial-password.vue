<script lang="ts" setup>
import { ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { Button, Form, FormItem, Input, message } from 'ant-design-vue';

import { updatePassword } from '#/api/edu/student/profile';
import { useAuthStore } from '#/store';
import { getPasswordFormError } from '#/utils/password-rule';

const authStore = useAuthStore();
const userStore = useUserStore();
const submitting = ref(false);
const leaving = ref(false);
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

async function handleSubmit() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value;
  const error = getPasswordFormError(
    oldPassword,
    newPassword,
    confirmPassword,
    userStore.userInfo?.username,
  );
  if (error) {
    message.warning(error);
    return;
  }

  submitting.value = true;
  try {
    await updatePassword({ oldPassword, newPassword });
    message.success('密码已修改，请重新登录');
    await authStore.logout(false);
  } finally {
    submitting.value = false;
  }
}

/** 不改密码，退出当前账号回到登录页（例如登错了账号） */
async function backToLogin() {
  leaving.value = true;
  try {
    await authStore.logout(false);
  } finally {
    leaving.value = false;
  }
}
</script>

<template>
  <div class="initial-password">
    <h2 class="initial-password__title">修改初始密码</h2>
    <p class="initial-password__desc">
      首次登录需要先修改初始密码，修改后用新密码重新登录。
    </p>
    <Form :model="passwordForm" layout="vertical" @finish="handleSubmit">
      <FormItem label="旧密码">
        <Input.Password
          v-model:value="passwordForm.oldPassword"
          autocomplete="current-password"
          placeholder="请输入旧密码"
        />
      </FormItem>
      <FormItem label="新密码">
        <Input.Password
          v-model:value="passwordForm.newPassword"
          autocomplete="new-password"
          placeholder="8–16 位，须同时包含字母和数字"
        />
      </FormItem>
      <FormItem label="确认新密码">
        <Input.Password
          v-model:value="passwordForm.confirmPassword"
          autocomplete="new-password"
          placeholder="请再次输入新密码"
        />
      </FormItem>
      <Button type="primary" html-type="submit" :loading="submitting" block>
        修改密码
      </Button>
      <Button
        class="initial-password__back"
        :disabled="submitting"
        :loading="leaving"
        block
        type="link"
        @click="backToLogin"
      >
        返回登录
      </Button>
    </Form>
  </div>
</template>

<style scoped>
/* 与登录页同一套页头：标题 24/600，一句说明，表单直接放在认证面板上（不再套卡片） */
.initial-password {
  width: 100%;
  max-width: 448px;
}

.initial-password__title {
  margin: 0;
  font-size: var(--nus-fs-display);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-display);
  color: var(--nus-text);
}

.initial-password__back {
  margin-top: var(--nus-space-2);
}

.initial-password__desc {
  margin: var(--nus-space-2) 0 var(--nus-space-6);
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
}
</style>
