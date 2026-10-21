<script lang="ts" setup>
import type { InfraRedisApi } from '#/api/infra/redis';

import { useDescription } from '#/components/description';

defineProps<{
  redisData?: InfraRedisApi.RedisMonitorInfo;
}>();

/** 指标值缺失时显示「—」，不显示空白 */
function orDash(value: unknown) {
  return value === undefined || value === null || value === ''
    ? '—'
    : String(value);
}

const [Descriptions] = useDescription({
  bordered: false,
  column: { xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 6 },
  layout: 'vertical',
  colon: false,
  class: 'redis-info',
  schema: [
    {
      field: 'info.redis_version',
      label: 'Redis 版本',
    },
    {
      field: 'info.redis_mode',
      label: '运行模式',
      render: (val) => (val === 'standalone' ? '单机' : '集群'),
    },
    {
      field: 'info.tcp_port',
      label: '端口',
    },
    {
      field: 'info.connected_clients',
      label: '客户端数',
    },
    {
      field: 'info.uptime_in_days',
      label: '运行时间（天）',
    },
    {
      field: 'info.used_memory_human',
      label: '使用内存',
    },
    {
      field: 'info.used_cpu_user_children',
      label: '处理器用时（秒）',
      render: (val) =>
        val === undefined || val === null
          ? '—'
          : Number.parseFloat(val).toFixed(2),
    },
    {
      field: 'info.maxmemory_human',
      label: '内存上限',
      // 0B 表示没有设置上限
      render: (val) => (val === '0B' ? '不限' : orDash(val)),
    },
    {
      field: 'info.aof_enabled',
      label: 'AOF 持久化',
      render: (val) => (val === '0' ? '未开启' : '已开启'),
    },
    {
      field: 'info.rdb_last_bgsave_status',
      label: '最近一次 RDB 备份',
      render: (val) => {
        if (!val) {
          return '—';
        }
        return val === 'ok' ? '成功' : '失败';
      },
    },
    {
      field: 'dbSize',
      label: '键数量',
    },
    {
      field: 'info.instantaneous_input_kbps',
      label: '网络流量（入 / 出）',
      render: (val, data) =>
        `${orDash(val)} / ${orDash(data?.info?.instantaneous_output_kbps)} KB/s`,
    },
  ],
});
</script>

<template>
  <Descriptions :data="redisData" />
</template>

<style scoped>
/* 概览：标签在上（辅助色）、数值在下（正文色，等宽数字） */
.redis-info :deep(.ant-descriptions-item-label) {
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
}

.redis-info :deep(.ant-descriptions-item-content) {
  font-weight: var(--nus-fw-medium);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text);
}
</style>
