<script setup lang="ts">
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { MarkdownView } from '#/components/markdown-view';

const props = defineProps<{
  content?: string;
  reasoningContent?: string;
}>();

const isExpanded = ref(true); // 默认展开

/** 判断是否应该显示组件 */
const shouldShowComponent = computed(() => {
  return props.reasoningContent && props.reasoningContent.trim() !== '';
});

/** 标题文本 */
const titleText = computed(() => {
  const hasReasoningContent =
    props.reasoningContent && props.reasoningContent.trim() !== '';
  const hasContent = props.content && props.content.trim() !== '';
  if (hasReasoningContent && !hasContent) {
    return '深度思考中';
  }
  return '已深度思考';
});

/** 切换展开/收起 */
function toggleExpanded() {
  isExpanded.value = !isExpanded.value;
}
</script>

<template>
  <div v-if="shouldShowComponent" class="reasoning">
    <!-- 标题栏 -->
    <button
      :aria-expanded="isExpanded"
      class="reasoning__header"
      type="button"
      @click="toggleExpanded"
    >
      <IconifyIcon icon="lucide:brain" class="reasoning__icon" />
      <span class="reasoning__title">{{ titleText }}</span>
      <IconifyIcon
        :class="{ 'is-expanded': isExpanded }"
        class="reasoning__chevron"
        icon="lucide:chevron-down"
      />
    </button>
    <!-- 内容区 -->
    <div v-show="isExpanded" class="reasoning__body">
      <MarkdownView
        v-if="props.reasoningContent"
        class="reasoning__content"
        :content="props.reasoningContent"
      />
    </div>
  </div>
</template>

<style scoped>
/* 思考过程（V1.0.1.6 界面规范）：辅助色的折叠区，左侧细线与正文区分，不用渐变与毛玻璃 */
.reasoning {
  margin-bottom: var(--nus-space-3);
}

.reasoning__header {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
  padding: 2px var(--nus-space-2) 2px var(--nus-space-1);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--nus-radius-sm);
  transition: var(--nus-transition-colors);
}

.reasoning__header:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.reasoning__icon {
  width: 14px;
  height: 14px;
  color: var(--nus-text-tertiary);
}

.reasoning__chevron {
  width: 14px;
  height: 14px;
  color: var(--nus-text-tertiary);
  transition: transform var(--nus-dur) var(--nus-ease);
}

.reasoning__chevron.is-expanded {
  transform: rotate(180deg);
}

.reasoning__body {
  max-height: 300px;
  padding: var(--nus-space-1) var(--nus-space-3);
  margin-top: var(--nus-space-1);
  overflow-y: auto;
  border-left: 2px solid var(--nus-border);
}

.reasoning__content {
  color: var(--nus-text-secondary);
}

.reasoning__content :deep(*) {
  color: inherit;
}
</style>
