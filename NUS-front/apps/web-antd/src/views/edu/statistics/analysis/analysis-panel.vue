<script lang="ts" setup>
/**
 * 学情分析页的面板（V1.0.1.6 界面规范）：淡边框面板，标题 15/600，下面一句辅助说明，操作放右侧。
 * 面板里不再套卡片，需要分组时用分隔线或凹陷底。
 */
defineProps<{
  description?: string;
  title: string;
}>();
</script>

<template>
  <section class="analysis-panel surface-panel">
    <header class="analysis-panel__header">
      <div class="analysis-panel__heading">
        <h2 class="analysis-panel__title">{{ title }}</h2>
        <p v-if="description" class="analysis-panel__description">
          {{ description }}
        </p>
      </div>
      <div v-if="$slots.extra" class="analysis-panel__extra">
        <slot name="extra"></slot>
      </div>
    </header>
    <slot></slot>
  </section>
</template>

<style scoped>
.analysis-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--nus-card-padding);
}

.analysis-panel__header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2) var(--nus-space-4);
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--nus-space-4);
}

.analysis-panel__heading {
  flex: 1 1 240px;
  min-width: 0;
}

.analysis-panel__title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.analysis-panel__description {
  margin: 2px 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.analysis-panel__extra {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
}
</style>
