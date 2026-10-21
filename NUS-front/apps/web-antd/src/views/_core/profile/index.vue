<script setup lang="ts">
import type { SystemUserProfileApi } from '#/api/system/user/profile';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Card, Tabs } from 'ant-design-vue';

import { getAuthPermissionInfoApi } from '#/api';
import { getUserProfile } from '#/api/system/user/profile';

import BaseInfo from './modules/base-info.vue';
import ProfileUser from './modules/profile-user.vue';
import ResetPwd from './modules/reset-pwd.vue';
import UserSocial from './modules/user-social.vue';

const userStore = useUserStore();
const activeName = ref('basicInfo');

/** 加载个人信息 */
const profile = ref<SystemUserProfileApi.UserProfileRespVO>();
async function loadProfile() {
  profile.value = await getUserProfile();
}

/** 刷新个人信息 */
async function refreshProfile() {
  // 加载个人信息
  await loadProfile();

  // 更新 store
  const authPermissionInfo = await getAuthPermissionInfoApi();
  userStore.setUserInfo(authPermissionInfo.user);
}

/** 初始化 */
onMounted(loadProfile);
</script>

<template>
  <Page
    auto-content-height
    description="查看账号信息，修改个人资料、密码与第三方账号绑定。"
    title="个人中心"
  >
    <div class="profile-layout">
      <!-- 左侧 个人信息 -->
      <Card class="profile-layout__aside" title="个人信息">
        <ProfileUser :profile="profile" @success="refreshProfile" />
      </Card>

      <!-- 右侧 标签页 -->
      <Card class="profile-layout__main">
        <Tabs v-model:active-key="activeName" class="profile-tabs">
          <Tabs.TabPane key="basicInfo" tab="基本设置">
            <BaseInfo :profile="profile" @success="refreshProfile" />
          </Tabs.TabPane>
          <Tabs.TabPane key="resetPwd" tab="密码设置">
            <ResetPwd />
          </Tabs.TabPane>
          <Tabs.TabPane key="userSocial" tab="社交绑定" force-render>
            <UserSocial @update:active-name="activeName = $event" />
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  </Page>
</template>

<style scoped>
/* 个人中心（V1.0.1.6 界面规范）：左侧账号信息，右侧设置；窄屏上下排列 */
.profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--nus-space-4);
  align-items: start;

  @media (width >= 1024px) {
    grid-template-columns: minmax(280px, 2fr) minmax(0, 3fr);
  }
}

.profile-tabs :deep(.ant-tabs-nav) {
  margin-top: calc(var(--nus-space-2) * -1);
}
</style>
