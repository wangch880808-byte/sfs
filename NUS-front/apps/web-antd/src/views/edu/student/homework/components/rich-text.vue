<script lang="ts" setup>
import { RichHtml } from '#/components/rich-html';

/**
 * 教师填写的内容（任务要求、评语）按正文排版，交给通用的 RichHtml：
 * 大图按宽度等比缩小、点击可放大预览，表格与代码块超宽时横向滚动。
 * 评语是多行纯文本，plain 时保留换行。
 */
withDefaults(defineProps<{ html: string; plain?: boolean }>(), {
  plain: false,
});
</script>

<template>
  <RichHtml :class="{ 'is-plain': plain }" :html="html" class="rich-text" />
</template>

<style scoped>
.rich-text.is-plain {
  white-space: pre-line;
}
</style>
