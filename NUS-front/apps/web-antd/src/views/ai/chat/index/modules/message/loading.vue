<script setup lang="ts">
import { Skeleton } from 'ant-design-vue';
</script>

<template>
  <div class="chat-loading" role="status">
    <p class="chat-loading__text">正在读取对话消息…</p>
    <Skeleton active :paragraph="{ rows: 4 }" />
  </div>
</template>

<style scoped>
.chat-loading {
  max-width: 880px;
  padding: var(--nus-space-6) var(--nus-space-4);
  margin: 0 auto;
}

.chat-loading__text {
  margin: 0 0 var(--nus-space-4);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}
</style>
