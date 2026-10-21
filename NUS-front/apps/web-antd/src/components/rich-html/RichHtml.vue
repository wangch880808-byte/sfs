<script lang="ts" setup>
import type { ImagePreviewTarget } from './rich-html';

import { computed, nextTick, ref } from 'vue';

import { Image, ImagePreviewGroup } from 'ant-design-vue';

import {
  isRichHtmlEmpty,
  prepareRichHtml,
  resolveImagePreview,
  toCssSize,
} from './rich-html';

/**
 * 教师、学生填写的富文本（任务内容、作答等）统一按正文排版显示：
 * - 图片、视频按容器宽度等比缩小，点图片打开预览，可放大、旋转，多图左右切换；
 * - 表格、代码块超宽时在自身区域内横向滚动，长单词与长链接自动换行；
 * - maxHeight 限高后内部滚动；内容为空时显示 emptyText，或使用 empty 插槽。
 * 安全过滤由 v-dompurify-html 负责。
 */
const props = withDefaults(
  defineProps<{
    emptyText?: string;
    html?: null | string;
    maxHeight?: number | string;
  }>(),
  {
    emptyText: '暂无内容',
    html: '',
    maxHeight: undefined,
  },
);

defineSlots<{ empty?: () => any }>();

const bodyRef = ref<HTMLElement>();
const isEmpty = computed(() => isRichHtmlEmpty(props.html));
const preparedHtml = computed(() =>
  isEmpty.value ? '' : prepareRichHtml(props.html),
);
const rootStyle = computed(() => {
  const maxHeight = toCssSize(props.maxHeight);
  return maxHeight ? { maxHeight, overflow: 'auto' } : undefined;
});

const preview = ref<ImagePreviewTarget>({ current: 0, items: [] });
const previewVisible = ref(false);
/** 每次打开都重建预览组，保证图片顺序与本次点击时一致 */
const previewKey = ref(0);

async function openPreview(target: ImagePreviewTarget) {
  previewVisible.value = false;
  preview.value = target;
  previewKey.value += 1;
  await nextTick();
  previewVisible.value = true;
}

function handleClick(event: MouseEvent) {
  if (!bodyRef.value) return;
  const target = resolveImagePreview(bodyRef.value, event.target);
  if (!target) return;
  event.preventDefault();
  void openPreview(target);
}

function handleVisibleChange(visible: boolean) {
  previewVisible.value = visible;
}
</script>

<template>
  <div :style="rootStyle" class="rich-html">
    <template v-if="isEmpty">
      <slot name="empty">
        <p class="rich-html__empty">{{ emptyText }}</p>
      </slot>
    </template>
    <div
      v-else
      ref="bodyRef"
      v-dompurify-html="preparedHtml"
      class="rich-html__body"
      @click="handleClick"
    ></div>
    <div v-if="preview.items.length > 0" class="rich-html__preview" hidden>
      <ImagePreviewGroup
        :key="previewKey"
        :preview="{
          visible: previewVisible,
          current: preview.current,
          onVisibleChange: handleVisibleChange,
        }"
      >
        <Image
          v-for="(src, index) in preview.items"
          :key="`${index}-${src}`"
          :src="src"
        />
      </ImagePreviewGroup>
    </div>
  </div>
</template>

<style scoped>
.rich-html {
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: break-word;
}

.rich-html__empty {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.rich-html__body :deep(p) {
  margin: 0 0 var(--nus-space-2);
}

.rich-html__body :deep(p:last-child) {
  margin-bottom: 0;
}

.rich-html__body :deep(:where(h1, h2, h3, h4, h5, h6)) {
  margin: var(--nus-space-4) 0 var(--nus-space-2);
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.rich-html__body :deep(:where(h1, h2, h3, h4, h5, h6):first-child) {
  margin-top: 0;
}

.rich-html__body :deep(:where(ul, ol)) {
  padding-left: 20px;
  margin: var(--nus-space-2) 0;
}

.rich-html__body :deep(li) {
  margin-bottom: var(--nus-space-1);
}

.rich-html__body :deep(:where(strong, b)) {
  font-weight: var(--nus-fw-semibold);
}

.rich-html__body :deep(a) {
  color: var(--nus-primary);
  overflow-wrap: anywhere;
  transition: var(--nus-transition-colors);
}

.rich-html__body :deep(a:hover) {
  color: var(--nus-primary-hover);
}

.rich-html__body :deep(blockquote) {
  padding-left: var(--nus-space-3);
  margin: var(--nus-space-2) 0;
  color: var(--nus-text-secondary);
  border-left: 2px solid var(--nus-border);
}

.rich-html__body :deep(hr) {
  margin: var(--nus-space-3) 0;
  border: 0;
  border-top: 1px solid var(--nus-border-subtle);
}

/* 图片、视频：按容器宽度等比缩小，不变形 */
.rich-html__body :deep(:where(img, video)) {
  max-width: 100%;
  height: auto;
  vertical-align: middle;
}

.rich-html__body :deep(img) {
  cursor: zoom-in;
  border-radius: var(--nus-radius);
}

.rich-html__body :deep(a img) {
  cursor: pointer;
}

.rich-html__body :deep(:where(iframe, embed, object)) {
  max-width: 100%;
}

.rich-html__body :deep(code) {
  font-family: var(--nus-font-mono);
  font-size: var(--nus-fs-dense);
}

.rich-html__body :deep(pre) {
  max-width: 100%;
  padding: var(--nus-space-3);
  margin: var(--nus-space-2) 0;
  overflow: auto;
  color: var(--nus-text);
  white-space: pre;
  background: var(--nus-bg-sunken);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius);
}

/* 表格：外层包装在超宽时横向滚动 */
.rich-html__body :deep(.rich-html__table-wrap) {
  max-width: 100%;
  margin: var(--nus-space-2) 0;
  overflow-x: auto;
}

.rich-html__body :deep(table) {
  border-collapse: collapse;
}

.rich-html__body :deep(:where(th, td)) {
  padding: 6px var(--nus-space-2);
  vertical-align: top;
  border: 1px solid var(--nus-border-subtle);
}

.rich-html__body :deep(th) {
  font-weight: var(--nus-fw-semibold);
  background: var(--nus-bg-sunken);
}
</style>
