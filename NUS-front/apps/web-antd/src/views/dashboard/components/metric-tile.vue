<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

import { computed } from 'vue';
import { RouterLink } from 'vue-router';

import { ChevronRight } from '@vben/icons';

/**
 * 指标（V1.0.1.6 界面规范）：.metric-card 的标签 13 次要色、数值 24/600 正文色、说明 12 辅助色，数字不上色。
 * 给了地址就整块可点，打开对应的列表；还没读到数值时显示“—”。
 */
interface Props {
  label: string;
  value?: null | number | string;
  /** 数值后的单位：份、条、门 */
  unit?: string;
  meta?: string;
  to?: RouteLocationRaw;
}

const props = withDefaults(defineProps<Props>(), {
  meta: '',
  to: undefined,
  unit: '',
  value: undefined,
});

const hasValue = computed(
  () => props.value !== undefined && props.value !== null && props.value !== '',
);
</script>

<template>
  <component
    :is="to ? RouterLink : 'div'"
    v-bind="to ? { to } : {}"
    :class="{ 'is-link': !!to }"
    class="metric-card metric-tile"
  >
    <span class="metric-card__label metric-tile__label">
      {{ label }}
      <ChevronRight v-if="to" aria-hidden="true" class="metric-tile__chevron" />
    </span>
    <span class="metric-card__value metric-tile__value">
      <template v-if="hasValue">
        {{ value }}<span v-if="unit" class="metric-tile__unit">{{ unit }}</span>
      </template>
      <span v-else class="metric-tile__placeholder">—</span>
    </span>
    <span v-if="meta" class="metric-card__meta metric-tile__meta">
      {{ meta }}
    </span>
  </component>
</template>

<style scoped>
.metric-tile {
  display: flex;
  flex-direction: column;
  min-width: 0;
  color: var(--nus-text);
  transition: var(--nus-transition-colors);
}

.metric-tile.is-link:hover {
  background-color: var(--nus-bg-hover);
  border-color: var(--nus-border);
}

.metric-tile.is-link:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 2px;
}

.metric-tile__label {
  display: flex;
  gap: var(--nus-space-1);
  align-items: center;
  justify-content: space-between;
}

.metric-tile__chevron {
  flex: none;
  width: 14px;
  height: 14px;
  color: var(--nus-text-tertiary);
  transition: var(--nus-transition-colors);
}

.metric-tile.is-link:hover .metric-tile__chevron {
  color: var(--nus-text-secondary);
}

.metric-tile__unit {
  margin-left: var(--nus-space-1);
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-regular);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.metric-tile__placeholder {
  color: var(--nus-text-tertiary);
}

.metric-tile__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
