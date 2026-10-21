<script setup lang="ts">
import type { Component } from 'vue';

import { computed } from 'vue';

import { CircleAlert, Inbox, SearchX } from '@vben/icons';

/**
 * 空状态（V1.0.1.6 界面规范）分三种：还没有数据（empty）、筛选没有结果（filtered）、前置条件不满足（blocked）。
 * 标题说清现在是什么情况，说明写原因与下一步；操作放在 action 插槽，主操作最多一个。
 */
interface Props {
  kind?: 'blocked' | 'empty' | 'filtered';
  title: string;
  description?: string;
  /** 放在卡片、表格或侧栏里时用紧凑尺寸 */
  compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  compact: false,
  description: '',
  kind: 'empty',
});

const ICONS: Record<NonNullable<Props['kind']>, Component> = {
  blocked: CircleAlert,
  empty: Inbox,
  filtered: SearchX,
};

const icon = computed(() => ICONS[props.kind]);
</script>

<template>
  <div
    :class="[`nus-empty--${kind}`, { 'is-compact': compact }]"
    class="nus-empty"
    role="status"
  >
    <component :is="icon" aria-hidden="true" class="nus-empty__icon" />
    <p class="nus-empty__title">{{ title }}</p>
    <p v-if="description || $slots.description" class="nus-empty__description">
      <slot name="description">{{ description }}</slot>
    </p>
    <div v-if="$slots.action" class="nus-empty__action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<style scoped>
.nus-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.nus-empty.is-compact {
  padding: 24px 16px;
}

.nus-empty__icon {
  width: 24px;
  height: 24px;
  margin-bottom: 12px;
  color: var(--nus-text-tertiary);
}

.nus-empty--blocked .nus-empty__icon {
  color: var(--nus-warning);
}

.nus-empty__title {
  margin: 0;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.nus-empty__description {
  max-width: 420px;
  margin: 4px 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.nus-empty__action {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 16px;
}
</style>
