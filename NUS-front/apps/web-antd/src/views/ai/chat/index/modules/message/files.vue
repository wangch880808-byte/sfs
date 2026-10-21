<script setup lang="ts">
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { getFileIcon, getFileNameFromUrl } from '@vben/utils';

const props = defineProps<{
  attachmentUrls?: string[];
}>();

/** 过滤掉空值的附件列表 */
const validAttachmentUrls = computed(() => {
  return (props.attachmentUrls || []).filter((url) => url && url.trim());
});

/** 点击文件 */
function handleFileClick(url: string) {
  window.open(url, '_blank');
}
</script>

<template>
  <div v-if="validAttachmentUrls.length > 0" class="msg-files">
    <button
      v-for="(url, index) in validAttachmentUrls"
      :key="index"
      :title="getFileNameFromUrl(url)"
      class="msg-files__item"
      type="button"
      @click="handleFileClick(url)"
    >
      <span class="msg-files__icon">
        <IconifyIcon :icon="getFileIcon(getFileNameFromUrl(url))" :size="16" />
      </span>
      <span class="msg-files__name">{{ getFileNameFromUrl(url) }}</span>
    </button>
  </div>
</template>

<style scoped>
/* 附件（V1.0.1.6 界面规范）：面板底加淡边框，悬停换悬停底；图标放在凹陷底的小方块里，用辅助色 */
.msg-files {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  margin-top: var(--nus-space-2);
}

.msg-files__item {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  min-width: 160px;
  max-width: 280px;
  padding: var(--nus-space-2);
  color: var(--nus-text);
  text-align: left;
  cursor: pointer;
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.msg-files__item:hover {
  background: var(--nus-bg-hover);
  border-color: var(--nus-border);
}

.msg-files__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--nus-text-tertiary);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-sm);
}

.msg-files__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  white-space: nowrap;
}
</style>
