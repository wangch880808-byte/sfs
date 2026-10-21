<script setup lang="ts">
import type { Recordable } from '@vben/types';

import type { SystemUserProfileApi } from '#/api/system/user/profile';

import { watch } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { updateUserProfile } from '#/api/system/user/profile';

import {
  normalizeProfileValues,
  profileEmailRule,
  profileMobileRule,
  profileSexRule,
} from './base-info-form';

const props = defineProps<{
  profile?: SystemUserProfileApi.UserProfileRespVO;
}>();
const emit = defineEmits<{
  (e: 'success'): void;
}>();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    labelWidth: 70,
  },
  schema: [
    {
      label: '用户名称',
      fieldName: 'nickname',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户名称',
      },
      rules: 'required',
    },
    {
      label: '用户手机',
      fieldName: 'mobile',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户手机',
      },
      rules: profileMobileRule,
    },
    {
      label: '用户邮箱',
      fieldName: 'email',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户邮箱',
      },
      rules: profileEmailRule,
    },
    {
      label: '用户性别',
      fieldName: 'sex',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.SYSTEM_USER_SEX, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: profileSexRule,
    },
  ],
  resetButtonOptions: {
    show: false,
  },
  submitButtonOptions: {
    content: '保存资料',
  },
  handleSubmit,
});

async function handleSubmit(values: Recordable<any>) {
  try {
    formApi.setLoading(true);
    // 提交表单
    await updateUserProfile(
      normalizeProfileValues(values) as SystemUserProfileApi.UpdateProfileReqVO,
    );
    // 关闭并提示
    emit('success');
    message.success('个人资料已保存');
  } catch (error) {
    console.error(error);
  } finally {
    formApi.setLoading(false);
  }
}

/** 监听 profile 变化 */
watch(
  () => props.profile,
  (newProfile) => {
    if (newProfile) {
      formApi.setValues(newProfile);
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="profile-form">
    <Form />
  </div>
</template>

<style scoped>
.profile-form {
  max-width: 480px;
  padding-top: var(--nus-space-2);
}
</style>
