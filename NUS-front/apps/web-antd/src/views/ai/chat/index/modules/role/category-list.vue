<script setup lang="ts">
import type { PropType } from 'vue';

defineProps({
  categoryList: {
    type: Array as PropType<string[]>,
    required: true,
  },
  active: {
    type: String,
    required: false,
    default: '全部',
  },
});

const emits = defineEmits(['onCategoryClick']); // 定义回调

/** 处理分类点击事件 */
async function handleCategoryClick(category: string) {
  emits('onCategoryClick', category);
}
</script>

<template>
  <div class="role-category" role="tablist" aria-label="角色类别">
    <button
      v-for="category in categoryList"
      :key="category"
      :aria-selected="category === active"
      :class="{ 'is-active': category === active }"
      class="role-category__item"
      role="tab"
      type="button"
      @click="handleCategoryClick(category)"
    >
      {{ category }}
    </button>
  </div>
</template>

<style scoped>
/* 类别筛选（V1.0.1.6 界面规范）：选中用选中底与主色字，不用胶囊形主按钮 */
.role-category {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
}

.role-category__item {
  height: 28px;
  padding: 0 var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--nus-border);
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.role-category__item:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.role-category__item.is-active {
  font-weight: var(--nus-fw-medium);
  color: var(--nus-primary);
  background: var(--nus-bg-selected);
  border-color: var(--nus-bg-selected);
}
</style>
