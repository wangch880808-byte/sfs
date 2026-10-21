<script lang="ts" setup>
import { computed, onActivated, onMounted } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { getAdminDashboard, getTotalPassRate } from '#/api/edu/statistics';
import { MetricTile } from '#/views/dashboard/components';
import { buildAdminAttention } from '#/views/dashboard/shared/admin';
import { getGreetingTitle } from '#/views/dashboard/shared/greeting';
import { ADMIN_LINKS, useAvailableLink } from '#/views/dashboard/shared/links';
import {
  combinePanelState,
  useAsyncData,
} from '#/views/dashboard/shared/use-async-data';

import AdminActivityCard from './components/admin-dashboard-activity-card.vue';
import AdminAttentionCard from './components/admin-dashboard-attention-card.vue';

/**
 * 管理员看板（V1.0.1.6 驾驶舱）：全校概况四个指标，提交与批改（含 AI 批改）和成绩分布，
 * 以及需要关注的事项（待审批的公共课申请、成绩预警）。没有权限的部分不读也不显示。
 * 待审批数由看板接口直接给出（V1.0.1.8）：后端按公共课审批权限判断，没有权限时为 null。
 */

const userStore = useUserStore();
const { hasAccessByCodes } = useAccess();
const linkOf = useAvailableLink();

const summary = useAsyncData(() => getAdminDashboard());
const passRate = useAsyncData(() => getTotalPassRate({}), {
  enabled: () => hasAccessByCodes(['edu:statistics:query']),
});
const sources = [summary, passRate];

function loadAll() {
  return Promise.all(sources.map((source) => source.load()));
}

let mounted = false;
onMounted(() => {
  void loadAll().then(() => {
    mounted = true;
  });
});
onActivated(() => {
  if (mounted) void loadAll();
});

const title = computed(() => getGreetingTitle(userStore.userInfo?.nickname));

const metrics = computed(() => {
  const data = summary.data.value;
  return [
    {
      key: 'students',
      label: '学生',
      meta: '启用中的账号',
      to: linkOf(ADMIN_LINKS.studentList),
      unit: '人',
      value: data?.totalStudents,
    },
    {
      key: 'teachers',
      label: '教师',
      meta: '启用中的账号',
      to: linkOf(ADMIN_LINKS.teacherList),
      unit: '人',
      value: data?.totalTeachers,
    },
    {
      key: 'courses',
      label: '课程',
      meta: '未封存的课程',
      to: linkOf(ADMIN_LINKS.courseList),
      unit: '门',
      value: data?.totalCourses,
    },
    {
      key: 'homeworks',
      label: '实训任务',
      meta: '已发布与已截止',
      unit: '项',
      value: data?.totalHomeworks,
    },
  ];
});

/** 能审批公共课的人（看板接口给出了待审批数）才把看板概况算进“需要关注” */
const canAuditPublicCourse = computed(
  () =>
    summary.data.value?.pendingPublicAuditCount !== null &&
    summary.data.value?.pendingPublicAuditCount !== undefined,
);
const attentionSources = computed(() => [
  ...(passRate.enabled.value ? [passRate] : []),
  ...(canAuditPublicCourse.value ? [summary] : []),
]);
const attentionItems = computed(() =>
  buildAdminAttention({
    passRate: passRate.data.value,
    pendingPublicAuditCount: summary.data.value?.pendingPublicAuditCount,
  }),
);
const attentionState = computed(() =>
  combinePanelState(attentionSources.value),
);
const summaryState = computed(() => combinePanelState([summary]));

function retryAttention() {
  return Promise.all(attentionSources.value.map((source) => source.load()));
}
</script>

<template>
  <Page
    :title="title"
    description="全校实训教学的概况，以及需要你处理的审批与预警。"
  >
    <div class="metric-grid lg:grid-cols-4">
      <MetricTile
        v-for="metric in metrics"
        :key="metric.key"
        :label="metric.label"
        :meta="metric.meta"
        :to="metric.to"
        :unit="metric.unit"
        :value="metric.value"
      />
    </div>

    <div class="mt-6 grid items-start gap-4 xl:grid-cols-3">
      <AdminActivityCard
        :class="attentionSources.length > 0 ? 'xl:col-span-2' : 'xl:col-span-3'"
        :error="summaryState.error"
        :loading="summaryState.loading"
        :pass-rate="passRate.data.value"
        :pass-rate-error="passRate.error.value"
        :pass-rate-loading="passRate.loading.value"
        :show-grades="passRate.enabled.value"
        :summary="summary.data.value"
        @retry="summary.load()"
        @retry-grades="passRate.load()"
      />
      <AdminAttentionCard
        v-if="attentionSources.length > 0"
        :error="attentionState.error"
        :items="attentionItems"
        :loading="attentionState.loading"
        @retry="retryAttention"
      />
    </div>
  </Page>
</template>
