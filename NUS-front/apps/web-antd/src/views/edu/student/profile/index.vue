<script lang="ts" setup>
import type { StudentProfileApi } from '#/api/edu/student/profile';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Avatar,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Form,
  FormItem,
  Input,
  message,
} from 'ant-design-vue';

import { getProfile, updatePassword } from '#/api/edu/student/profile';
import { useAuthStore } from '#/store';
import { getPasswordFormError } from '#/utils/password-rule';

const authStore = useAuthStore();
const profile = ref<StudentProfileApi.ProfileInfo>();
const loading = ref(false);

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});
const submitting = ref(false);

async function loadProfile() {
  loading.value = true;
  try {
    profile.value = await getProfile();
  } finally {
    loading.value = false;
  }
}

async function handleChangePassword() {
  const error = getPasswordFormError(
    passwordForm.value.oldPassword,
    passwordForm.value.newPassword,
    passwordForm.value.confirmPassword,
    profile.value?.username,
  );
  if (error) {
    message.warning(error);
    return;
  }
  submitting.value = true;
  try {
    await updatePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    });
    message.success('密码已修改，请重新登录');
    await authStore.logout(false);
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadProfile();
});
</script>

<template>
  <Page
    auto-content-height
    description="查看你的学籍信息，修改登录密码。"
    title="个人中心"
  >
    <div class="profile-grid">
      <Card :loading="loading" title="个人信息">
        <div v-if="profile" class="profile-info">
          <Avatar :size="64" :src="profile.avatar" class="profile-avatar">
            {{ profile.nickname?.charAt(0) }}
          </Avatar>
          <Descriptions :column="{ xs: 1, sm: 2 }" class="profile-desc">
            <DescriptionsItem label="姓名">
              {{ profile.nickname }}
            </DescriptionsItem>
            <DescriptionsItem label="学号">
              {{ profile.username }}
            </DescriptionsItem>
            <DescriptionsItem label="院系">
              {{ profile.deptName }}
            </DescriptionsItem>
            <DescriptionsItem label="班级">
              {{ profile.className }}
            </DescriptionsItem>
            <DescriptionsItem label="入学年份">
              {{ profile.gradeYear ? `${profile.gradeYear} 级` : '-' }}
            </DescriptionsItem>
            <DescriptionsItem label="手机">
              {{ profile.mobile || '-' }}
            </DescriptionsItem>
            <DescriptionsItem label="邮箱">
              {{ profile.email || '-' }}
            </DescriptionsItem>
          </Descriptions>
        </div>
      </Card>

      <Card title="修改密码">
        <p class="profile-hint">修改成功后需要用新密码重新登录。</p>
        <Form class="profile-form" layout="vertical">
          <FormItem label="原密码">
            <Input.Password
              v-model:value="passwordForm.oldPassword"
              autocomplete="current-password"
              placeholder="请输入原密码"
            />
          </FormItem>
          <FormItem extra="8–16 位，须同时包含字母和数字。" label="新密码">
            <Input.Password
              v-model:value="passwordForm.newPassword"
              autocomplete="new-password"
              placeholder="请输入新密码"
            />
          </FormItem>
          <FormItem label="确认密码">
            <Input.Password
              v-model:value="passwordForm.confirmPassword"
              autocomplete="new-password"
              placeholder="请再次输入新密码"
            />
          </FormItem>
          <FormItem class="profile-form__actions">
            <Button
              :loading="submitting"
              type="primary"
              @click="handleChangePassword"
            >
              确认修改
            </Button>
          </FormItem>
        </Form>
      </Card>
    </div>
  </Page>
</template>

<style scoped>
.profile-grid {
  display: grid;
  gap: 16px;
  align-items: start;
}

.profile-info {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.profile-avatar {
  flex: none;
}

.profile-desc {
  flex: 1;
  min-width: 0;
}

.profile-hint {
  margin: 0 0 16px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.profile-form {
  max-width: 360px;
}

.profile-form__actions {
  margin-bottom: 0;
}

@media (width >= 1280px) {
  .profile-grid {
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  }
}

@media (width <= 640px) {
  .profile-info {
    flex-direction: column;
  }
}
</style>
