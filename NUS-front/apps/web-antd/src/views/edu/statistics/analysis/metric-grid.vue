<script lang="ts" setup>
import type { AnalysisMetric } from './types';

import { StatusTag } from '#/components/status-tag';

/**
 * 学情分析页的指标卡（V1.0.1.6 界面规范）：一行最多 4 个，标签 13 次要色、数值 24/600 正文色、说明 12 辅助色。
 * 状态颜色只给圆点：status 在标签右侧显示圆点＋文字，tone 在数值前加圆点。
 */
defineProps<{
  items: AnalysisMetric[];
}>();
</script>

<template>
  <div class="metric-grid">
    <div v-for="item in items" :key="item.label" class="metric-card">
      <div class="analysis-metric__head">
        <span class="metric-card__label">{{ item.label }}</span>
        <StatusTag
          v-if="item.status"
          :label="item.status.label"
          :tone="item.status.tone"
        />
      </div>
      <div class="metric-card__value analysis-metric__value">
        <span
          v-if="item.tone"
          :class="`is-${item.tone}`"
          aria-hidden="true"
          class="analysis-metric__dot"
        ></span>
        <span class="analysis-metric__text">{{ item.value }}</span>
      </div>
      <div v-if="item.meta" class="metric-card__meta">{{ item.meta }}</div>
    </div>
  </div>
</template>

<style scoped>
.analysis-metric__head {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.analysis-metric__value {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  min-width: 0;
}

.analysis-metric__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-metric__dot {
  flex: none;
  width: 8px;
  height: 8px;
  background: var(--nus-status-neutral);
  border-radius: 50%;
}

.analysis-metric__dot.is-running {
  background: var(--nus-status-running);
}

.analysis-metric__dot.is-waiting {
  background: var(--nus-status-waiting);
}

.analysis-metric__dot.is-done {
  background: var(--nus-status-done);
}

.analysis-metric__dot.is-failed {
  background: var(--nus-status-failed);
}
</style>
