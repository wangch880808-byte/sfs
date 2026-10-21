<script lang="ts" setup>
import type { FactItem } from '../task-display';

/**
 * 属性列表：左边标签（次要色）、右边取值（正文色），一行一项。
 * 某一项要自定义显示（比如状态）时给它 slot 名，用同名插槽渲染；inline 时横排，用于页面里的时间信息。
 */
withDefaults(defineProps<{ inline?: boolean; items: FactItem[] }>(), {
  inline: false,
});
</script>

<template>
  <dl :class="{ 'is-inline': inline }" class="fact-list">
    <div v-for="item in items" :key="item.label" class="fact-list__row">
      <dt class="fact-list__label">{{ item.label }}</dt>
      <dd class="fact-list__value">
        <slot v-if="item.slot" :item="item" :name="item.slot"></slot>
        <template v-else>{{ item.value }}</template>
      </dd>
    </div>
  </dl>
</template>

<style scoped>
.fact-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.fact-list__row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 32px;
}

.fact-list__label {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.fact-list__value {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

/* 横排：一行放几项，标签与取值挨着 */
.fact-list.is-inline {
  flex-flow: row wrap;
  gap: 4px 24px;
}

.fact-list.is-inline .fact-list__row {
  display: flex;
  gap: 8px;
  min-height: 0;
}
</style>
