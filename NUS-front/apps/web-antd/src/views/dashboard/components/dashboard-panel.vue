<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

import { RouterLink } from 'vue-router';

import { ChevronRight, CircleAlert } from '@vben/icons';

import { Button, Spin } from 'ant-design-vue';

/**
 * 看板面板（V1.0.1.6 界面规范）：面板色、1px 淡边框、圆角 8；标题 15/600，旁边是数量，右侧“查看全部”。
 * 第一次读取时写明在读什么；没读到时只让这一块降级，给出原因和重试；没有数据时由 empty 插槽放 EmptyState。
 */
interface Props {
  title: string;
  /** 标题旁的数量或说明，如“2 份”“7 条未读” */
  count?: number | string;
  /** 右上角“查看全部”的文字与地址（地址打不开时不传） */
  moreText?: string;
  moreTo?: RouteLocationRaw;
  loading?: boolean;
  /** 加载时的说明：“正在读取待批改的提交…” */
  loadingText?: string;
  error?: boolean;
  /** 没读到时的标题：“待批改的提交没有读到” */
  errorTitle?: string;
  /** 没有数据：显示 empty 插槽 */
  empty?: boolean;
}

withDefaults(defineProps<Props>(), {
  count: undefined,
  empty: false,
  error: false,
  errorTitle: '这部分内容没有读到',
  loading: false,
  loadingText: '正在读取…',
  moreText: '查看全部',
  moreTo: undefined,
});

defineEmits<{ retry: [] }>();
</script>

<template>
  <section :aria-busy="loading" class="dash-panel surface-panel">
    <header class="dash-panel__header">
      <div class="dash-panel__heading">
        <h2 class="dash-panel__title">{{ title }}</h2>
        <span
          v-if="count !== undefined && count !== '' && !loading && !error"
          class="dash-panel__count"
        >
          {{ count }}
        </span>
      </div>
      <div class="dash-panel__extra">
        <slot name="extra">
          <RouterLink v-if="moreTo" :to="moreTo" class="dash-panel__more">
            {{ moreText }}
            <ChevronRight aria-hidden="true" class="dash-panel__more-icon" />
          </RouterLink>
        </slot>
      </div>
    </header>

    <div v-if="loading" class="dash-panel__state" role="status">
      <Spin size="small" />
      <span>{{ loadingText }}</span>
    </div>
    <div
      v-else-if="error"
      class="dash-panel__state dash-panel__state--error"
      role="alert"
    >
      <CircleAlert aria-hidden="true" class="dash-panel__error-icon" />
      <p class="dash-panel__error-title">{{ errorTitle }}</p>
      <p class="dash-panel__error-text">网络或服务暂时不可用，可以稍后重试。</p>
      <Button size="small" @click="$emit('retry')">重试</Button>
    </div>
    <div v-else-if="empty" class="dash-panel__empty">
      <slot name="empty"></slot>
    </div>
    <div v-else class="dash-panel__body">
      <slot></slot>
    </div>

    <footer
      v-if="$slots.footer && !loading && !error"
      class="dash-panel__footer"
    >
      <slot name="footer"></slot>
    </footer>
  </section>
</template>

<style scoped>
.dash-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.dash-panel__header {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  min-height: 52px;
  padding: var(--nus-space-3) var(--nus-card-padding);
}

.dash-panel__heading {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
  min-width: 0;
}

.dash-panel__title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.dash-panel__count {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.dash-panel__extra {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  margin-left: auto;
}

.dash-panel__more {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  white-space: nowrap;
  border-radius: var(--nus-radius-sm);
  transition: var(--nus-transition-colors);
}

.dash-panel__more:hover {
  color: var(--nus-primary);
}

.dash-panel__more:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 2px;
}

.dash-panel__more-icon {
  width: 14px;
  height: 14px;
}

.dash-panel__state {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: center;
  padding: var(--nus-space-8) var(--nus-card-padding);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
  border-top: 1px solid var(--nus-border-subtle);
}

.dash-panel__state--error {
  flex-direction: column;
  gap: 0;
  text-align: center;
}

.dash-panel__error-icon {
  width: 20px;
  height: 20px;
  margin-bottom: var(--nus-space-2);
  color: var(--nus-text-tertiary);
}

.dash-panel__error-title {
  margin: 0;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.dash-panel__error-text {
  margin: var(--nus-space-1) 0 var(--nus-space-3);
}

.dash-panel__empty {
  border-top: 1px solid var(--nus-border-subtle);
}

.dash-panel__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.dash-panel__footer {
  padding: var(--nus-space-3) var(--nus-card-padding);
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
  border-top: 1px solid var(--nus-border-subtle);
}
</style>
