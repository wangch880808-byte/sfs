<script setup lang="ts">
import { computed } from 'vue';

import { getDictObj } from '@vben/hooks';

import { StatusTag } from '#/components/status-tag';
import { getDictStatusToneOf } from '#/utils/status';

interface DictTagProps {
  type: string; // 字典类型
  value: any; // 字典值
  icon?: string; // 图标
}

const props = defineProps<DictTagProps>();

/**
 * 字典值的显示（V1.0.1.6 界面规范）：状态类字典显示为圆点＋文字（类别见 utils/status.ts），
 * 其余字典按普通文字显示，不再用彩色标签。
 */
const dictTag = computed(() => {
  // 校验参数有效性
  if (!props.type || props.value === undefined || props.value === null) {
    return null;
  }

  // 获取字典对象
  const dict = getDictObj(props.type, String(props.value));
  if (!dict) {
    return null;
  }

  return {
    label: dict.label || '',
    tone: getDictStatusToneOf(props.type, props.value, dict.colorType),
  };
});
</script>

<template>
  <template v-if="dictTag">
    <StatusTag
      v-if="dictTag.tone"
      :label="dictTag.label"
      :tone="dictTag.tone"
    />
    <span v-else class="dict-text">{{ dictTag.label }}</span>
  </template>
</template>
