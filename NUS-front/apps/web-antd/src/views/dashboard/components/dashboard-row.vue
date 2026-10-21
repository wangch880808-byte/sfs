<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

import { computed } from 'vue';
import { RouterLink } from 'vue-router';

import { ChevronRight } from '@vben/icons';

/**
 * 看板列表的一行（V1.0.1.6 界面规范）：整行可点（链接或按钮），悬停用悬停底、带过渡；
 * 左边标题（14/500）与说明（12 辅助色），右边是状态、数量或时间，最右是动作提示。
 */
interface Props {
  /** 整行打开的地址 */
  to?: RouteLocationRaw;
  /** 没有地址但可以点（点击交给父组件处理） */
  clickable?: boolean;
  /** 行尾的动作提示：“批改”“去提交” */
  action?: string;
}

const props = withDefaults(defineProps<Props>(), {
  action: '',
  clickable: false,
  to: undefined,
});

defineEmits<{ click: [event: MouseEvent] }>();

const tag = computed(() => {
  if (props.to) return RouterLink;
  return props.clickable ? 'button' : 'div';
});

const attrs = computed(() => {
  if (props.to) return { to: props.to };
  return props.clickable ? { type: 'button' } : {};
});

const interactive = computed(() => !!props.to || props.clickable);
</script>

<template>
  <component
    :is="tag"
    v-bind="attrs"
    :class="{ 'is-interactive': interactive }"
    class="dash-row"
    @click="$emit('click', $event)"
  >
    <span v-if="$slots.leading" class="dash-row__leading">
      <slot name="leading"></slot>
    </span>
    <span class="dash-row__main">
      <span class="dash-row__title"><slot></slot></span>
      <span v-if="$slots.meta" class="dash-row__meta">
        <slot name="meta"></slot>
      </span>
    </span>
    <span v-if="$slots.aside" class="dash-row__aside">
      <slot name="aside"></slot>
    </span>
    <span v-if="action" class="dash-row__action">
      {{ action }}
      <ChevronRight aria-hidden="true" class="dash-row__chevron" />
    </span>
  </component>
</template>

<style scoped>
.dash-row {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  width: 100%;
  min-height: 56px;
  padding: var(--nus-space-2) var(--nus-card-padding);
  margin: 0;
  font: inherit;
  color: var(--nus-text);
  text-align: left;
  background-color: transparent;
  border: 0;
  border-top: 1px solid var(--nus-border-subtle);
  transition: var(--nus-transition-colors);
}

.dash-row.is-interactive {
  cursor: pointer;
}

.dash-row.is-interactive:hover {
  background-color: var(--nus-bg-hover);
}

.dash-row.is-interactive:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: -2px;
}

.dash-row__leading {
  display: flex;
  flex: none;
  align-items: center;
}

.dash-row__main {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.dash-row__title,
.dash-row__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dash-row__title {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
}

.dash-row__meta {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.dash-row__aside {
  display: flex;
  flex: none;
  gap: var(--nus-space-3);
  align-items: center;
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  white-space: nowrap;
}

.dash-row__action {
  display: inline-flex;
  flex: none;
  gap: 2px;
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-primary);
  white-space: nowrap;
  transition: var(--nus-transition-colors);
}

.dash-row.is-interactive:hover .dash-row__action {
  color: var(--nus-primary-hover);
}

.dash-row__chevron {
  width: 14px;
  height: 14px;
}
</style>
