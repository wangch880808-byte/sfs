<script lang="ts" setup>
import { computed, defineAsyncComponent } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { EmptyState } from '#/components/empty-state';
import { resolvePersona } from '#/utils/persona';

import { getGreetingTitle } from '../shared/greeting';

/**
 * 工作台：按角色显示管理员看板、教师工作台或学生首页（各自带页头与问候），
 * 兼任多个角色时按“管理员 > 教师 > 学生”取一个（utils/persona.ts）；
 * 没有这三类角色的账号显示前置条件不满足的空状态。
 */
const userStore = useUserStore();

const home = computed(() => resolvePersona(userStore.userRoles).home);

const AdminDashboard = defineAsyncComponent(
  () => import('#/views/edu/statistics/admin-dashboard.vue'),
);
const TeacherDashboard = defineAsyncComponent(
  () => import('#/views/edu/statistics/teacher-dashboard.vue'),
);
const StudentHome = defineAsyncComponent(
  () => import('#/views/edu/student/home/index.vue'),
);

const title = computed(() => getGreetingTitle(userStore.userInfo?.nickname));
</script>

<template>
  <AdminDashboard v-if="home === 'admin'" />
  <TeacherDashboard v-else-if="home === 'teacher'" />
  <StudentHome v-else-if="home === 'student'" />
  <Page v-else :title="title" description="这里会按你的角色显示要处理的事。">
    <div class="surface-panel">
      <EmptyState
        description="当前账号还没有分配管理员、教师或学生角色，请联系管理员分配后重新登录。"
        kind="blocked"
        title="还没有可用的工作台"
      />
    </div>
  </Page>
</template>
