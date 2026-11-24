<script lang="ts" setup>
import type { EduWorkspaceApi } from '#/api/edu/workspace';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Card, Spin, Table } from 'ant-design-vue';

import { getWorkspaceRuntime } from '#/api/edu/workspace';

const loading = ref(false);
const runtime = ref<EduWorkspaceApi.RuntimeResp | null>(null);

const statCards = [
  { key: 'weekClassCount', label: '本周课堂', unit: '个' },
  { key: 'weekSubmissionCount', label: '本周提交', unit: '次' },
  { key: 'weekGradingCount', label: '本周批改', unit: '次' },
  { key: 'weekAiCallCount', label: 'AI 调用', unit: '次' },
] as const;

const columns = [
  { title: '日期', dataIndex: 'date', key: 'date' },
  { title: '提交数', dataIndex: 'submissions', key: 'submissions' },
  { title: '批改数', dataIndex: 'gradings', key: 'gradings' },
];

async function load() {
  loading.value = true;
  try {
    runtime.value = await getWorkspaceRuntime();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Page title="运行概况" description="近 7 天的教学运行数据概览。">
    <Spin :spinning="loading">
      <!-- 统计卡 -->
      <div class="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Card v-for="card in statCards" :key="card.key">
          <div class="text-sm text-gray-500">{{ card.label }}</div>
          <div class="mt-1 text-2xl font-bold">
            {{ runtime?.[card.key] ?? 0 }}
            <span class="ml-1 text-sm font-normal text-gray-400">
              {{ card.unit }}
            </span>
          </div>
        </Card>
      </div>

      <!-- 每日趋势 -->
      <Card title="每日趋势">
        <Table
          :columns="columns"
          :data-source="runtime?.dailyStats ?? []"
          :pagination="false"
          row-key="date"
          size="small"
        />
      </Card>
    </Spin>
  </Page>
</template>
