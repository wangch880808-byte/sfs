<script lang="ts" setup>
import type { InfraRedisApi } from '#/api/infra/redis';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Spin } from 'ant-design-vue';

import { getRedisMonitorInfo } from '#/api/infra/redis';
import { EmptyState } from '#/components/empty-state';

import Commands from './modules/commands.vue';
import Info from './modules/info.vue';
import Memory from './modules/memory.vue';

const redisData = ref<InfraRedisApi.RedisMonitorInfo>();
const loading = ref(false);
const loadFailed = ref(false);

/** 统一加载 Redis 数据 */
async function loadRedisData() {
  loading.value = true;
  loadFailed.value = false;
  try {
    redisData.value = await getRedisMonitorInfo();
  } catch (error) {
    loadFailed.value = true;
    console.error('加载 Redis 数据失败', error);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadRedisData();
});
</script>

<template>
  <Page
    auto-content-height
    description="查看缓存服务的运行状态、内存占用与命令调用分布。"
    title="Redis 监控"
  >
    <template #extra>
      <Button :loading="loading" @click="loadRedisData">刷新</Button>
    </template>

    <Card v-if="loadFailed && !redisData">
      <EmptyState
        description="缓存服务可能没有连接上。稍后点「重新读取」，仍然失败请检查 Redis 是否在运行。"
        kind="blocked"
        title="没有读到 Redis 运行数据"
      >
        <template #action>
          <Button :loading="loading" @click="loadRedisData">重新读取</Button>
        </template>
      </EmptyState>
    </Card>

    <template v-else>
      <Spin :spinning="loading && !redisData" tip="正在读取 Redis 运行状态…">
        <Card title="运行概览">
          <Info :redis-data="redisData" />
        </Card>

        <div class="redis-charts">
          <Card title="内存使用">
            <Memory :redis-data="redisData" />
          </Card>

          <Card title="命令统计">
            <Commands :redis-data="redisData" />
          </Card>
        </div>
      </Spin>
    </template>
  </Page>
</template>

<style scoped>
.redis-charts {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--nus-space-4);
  margin-top: var(--nus-space-4);

  @media (width >= 768px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
