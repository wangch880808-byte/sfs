<script setup lang="ts">
import type { StyleValue } from 'vue';

import type { PageProps } from './types';

import { computed, nextTick, onMounted, ref, useTemplateRef } from 'vue';

import { CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT } from '@vben-core/shared/constants';
import { cn } from '@vben-core/shared/utils';

defineOptions({
  name: 'Page',
});

const { autoContentHeight = false, heightOffset = 0 } =
  defineProps<PageProps>();

const headerHeight = ref(0);
const footerHeight = ref(0);
const docHeight = ref(0);
const shouldAutoHeight = ref(false);

const headerRef = useTemplateRef<HTMLDivElement>('headerRef');
const footerRef = useTemplateRef<HTMLDivElement>('footerRef');
const docRef = useTemplateRef<HTMLDivElement>('docRef');

const contentStyle = computed<StyleValue>(() => {
  if (autoContentHeight) {
    return {
      height: `calc(var(${CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT}) - ${headerHeight.value}px - ${footerHeight.value}px - ${docHeight.value}px - ${typeof heightOffset === 'number' ? `${heightOffset}px` : heightOffset})`,
      overflowY: shouldAutoHeight.value ? 'auto' : 'unset',
    };
  }
  return {};
});

async function calcContentHeight() {
  if (!autoContentHeight) {
    return;
  }
  await nextTick();
  headerHeight.value = headerRef.value?.offsetHeight || 0;
  footerHeight.value = footerRef.value?.offsetHeight || 0;
  docHeight.value = docRef.value?.offsetHeight || 0;
  setTimeout(() => {
    shouldAutoHeight.value = true;
  }, 30);
}

onMounted(() => {
  calcContentHeight();
});
</script>

<template>
  <div class="vben-page relative flex min-h-full flex-col">
    <div
      v-if="$slots.doc"
      ref="docRef"
      :class="
        cn(
          'surface-panel relative mx-4 mt-4 flex items-start overflow-hidden lg:mx-6 lg:mt-6',
        )
      "
    >
      <div class="flex-auto">
        <slot name="doc"></slot>
      </div>
    </div>

    <!-- 页头直接放在画布上（不套卡片）：标题 20/600，一句说明 13px 辅助色，操作放右侧 -->
    <div
      v-if="
        description ||
        $slots.description ||
        title ||
        $slots.title ||
        $slots.extra
      "
      ref="headerRef"
      :class="
        cn(
          'vben-page__header relative flex flex-wrap items-start justify-between gap-x-4 gap-y-3 px-4 pt-4 lg:px-6 lg:pt-6',
          headerClass,
        )
      "
    >
      <div class="min-w-0 flex-auto">
        <slot name="title">
          <h1 v-if="title" class="vben-page__title">
            {{ title }}
          </h1>
        </slot>

        <slot name="description">
          <p v-if="description" class="vben-page__description">
            {{ description }}
          </p>
        </slot>
      </div>

      <div v-if="$slots.extra" class="flex flex-wrap items-center gap-2">
        <slot name="extra"></slot>
      </div>
    </div>

    <div
      :class="cn('vben-page__content h-full p-4 lg:p-6', contentClass)"
      :style="contentStyle"
    >
      <slot></slot>
    </div>
    <div
      v-if="$slots.footer"
      ref="footerRef"
      :class="
        cn(
          'surface-panel align-center mx-4 mb-4 mt-4 flex px-6 py-4 lg:mx-6 lg:mb-6',
          footerClass,
        )
      "
    >
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<style scoped>
.vben-page__title {
  margin: 0;
  font-size: var(--nus-fs-title, 20px);
  font-weight: 600;
  line-height: var(--nus-lh-title, 28px);
  color: hsl(var(--foreground));
}

.vben-page__description {
  margin: 2px 0 0;
  font-size: var(--nus-fs-dense, 13px);
  line-height: var(--nus-lh-dense, 20px);
  color: var(--nus-text-tertiary, hsl(var(--muted-foreground)));
}
</style>
