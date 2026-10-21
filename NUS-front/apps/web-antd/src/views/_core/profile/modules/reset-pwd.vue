<script setup lang="ts">
import type { Recordable } from '@vben/types';

import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { useVbenForm, z } from '#/adapter/form';
import { updateUserPassword } from '#/api/system/user/profile';
import { useAuthStore } from '#/store';
import { getNewPasswordError } from '#/utils/password-rule';

const authStore = useAuthStore();
const userStore = useUserStore();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    labelWidth: 70,
  },
  schema: [
    {
      component: 'InputPassword',
      fieldName: 'oldPassword',
      label: '旧密码',
      rules: z.string({ message: '请输入旧密码' }).min(1, '请输入旧密码'),
    },
    {
      component: 'InputPassword',
      dependencies: {
        rules(values) {
          return z
            .string({ message: '请输入新密码' })
            .refine(
              (value) =>
                !getNewPasswordError(
                  value,
                  values.oldPassword,
                  userStore.userInfo?.username,
                ),
              '新密码须为 8–16 位，包含字母和数字，且不能与用户名或旧密码相同',
            );
        },
        triggerFields: ['newPassword', 'oldPassword'],
      },
      fieldName: 'newPassword',
      label: '新密码',
      rules: 'required',
    },
    {
      component: 'InputPassword',
      dependencies: {
        rules(values) {
          return z
            .string({ message: '请输入确认密码' })
            .min(1, '请输入确认密码')
            .refine(
              (value) => value === values.newPassword,
              '新密码和确认密码不一致',
            );
        },
        triggerFields: ['newPassword', 'confirmPassword'],
      },
      fieldName: 'confirmPassword',
      label: '确认密码',
      rules: 'required',
    },
  ],
  resetButtonOptions: {
    show: false,
  },
  submitButtonOptions: {
    content: '修改密码',
  },
  handleSubmit,
});

async function handleSubmit(values: Recordable<any>) {
  try {
    formApi.setLoading(true);
    // 提交表单
    await updateUserPassword({
      oldPassword: values.oldPassword,
      newPassword: values.newPassword,
    });
    message.success('密码已修改，请重新登录');
    await authStore.logout(false);
  } catch (error) {
    console.error(error);
  } finally {
    formApi.setLoading(false);
  }
}
</script>

<template>
  <div class="profile-form">
    <p class="profile-form__hint">修改成功后需要用新密码重新登录。</p>
    <Form />
  </div>
</template>

<style scoped>
.profile-form {
  max-width: 480px;
  padding-top: var(--nus-space-2);
}

.profile-form__hint {
  margin: 0 0 var(--nus-space-4);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}
</style>
