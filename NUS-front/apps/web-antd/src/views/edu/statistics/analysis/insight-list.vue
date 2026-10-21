<script lang="ts" setup>
import type { AnalysisInsight } from './types';

import { StatusTag } from '#/components/status-tag';
import { getInsightStatusTone } from '#/utils/status-statistics';

/**
 * 学情分析页的结论列表（V1.0.1.6 界面规范）：每条一个圆点（语气）＋标题，下面一句说明；条目之间用分隔线，不再套卡片。
 */
defineProps<{
  items: AnalysisInsight[];
}>();
</script>

<template>
  <ul class="insight-list">
    <li v-for="item in items" :key="item.title" class="insight-list__item">
      <StatusTag
        :label="item.title"
        :tone="getInsightStatusTone(item.tone)"
        class="insight-list__title"
      />
      <p class="insight-list__detail">{{ item.detail }}</p>
    </li>
  </ul>
</template>

<style scoped>
.insight-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.insight-list__item {
  padding: var(--nus-space-3) 0;
}

.insight-list__item:first-child {
  padding-top: 0;
}

.insight-list__item:last-child {
  padding-bottom: 0;
}

.insight-list__item + .insight-list__item {
  border-top: 1px solid var(--nus-border-subtle);
}

.insight-list .insight-list__title {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
}

/* 说明与标题文字对齐（圆点 6 + 间距 6） */
.insight-list__detail {
  padding-left: 12px;
  margin: 2px 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}
</style>
