<script lang="ts" setup>
import type { EduWorkspaceApi } from '#/api/edu/workspace';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Spin } from 'ant-design-vue';

import { getWorkspaceTodos } from '#/api/edu/workspace';

const router = useRouter();
const loading = ref(false);
const todos = ref<EduWorkspaceApi.TodoItem[]>([]);

const categoryIcons: Record<string, string> = {
  grading: '📝',
  public_audit: '📋',
  resource_review: '📁',
};

async function load() {
  loading.value = true;
  try {
    const data = await getWorkspaceTodos();
    todos.value = data.todos ?? [];
  } finally {
    loading.value = false;
  }
}

function go(route: string) {
  if (route) router.push(route);
}

onMounted(load);
</script>

<template>
  <Page title="待办事项" description="需要你处理的审批、审核与批改事项。">
    <Spin :spinning="loading">
      <div v-if="todos.length === 0 && !loading" class="py-12 text-center text-gray-400">
        暂无待办事项
      </div>
      <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <Card v-for="item in todos" :key="item.category" hoverable>
          <div class="flex items-center gap-3">
            <span class="text-3xl">{{ categoryIcons[item.category] ?? '📌' }}</span>
            <div class="flex-1">
              <div class="text-sm text-gray-500">{{ item.label }}</div>
              <div class="text-2xl font-bold">{{ item.count }}</div>
            </div>
            <Button type="primary" size="small" @click="go(item.route)">
              进入处理
            </Button>
          </div>
        </Card>
      </div>
    </Spin>
  </Page>
</template>
