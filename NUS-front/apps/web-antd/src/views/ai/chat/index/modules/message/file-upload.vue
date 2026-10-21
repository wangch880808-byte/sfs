<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { formatFileSize, getFileIcon } from '@vben/utils';

import { message } from 'ant-design-vue';

import { useUpload } from '#/components/upload/use-upload';
import { getRequestErrorReason } from '#/utils/request-error';

export interface FileItem {
  name: string;
  size: number;
  url?: string;
  uploading?: boolean;
  progress?: number;
  raw?: File;
}

const props = withDefaults(
  defineProps<{
    acceptTypes?: string;
    disabled?: boolean;
    limit?: number;
    maxSize?: number;
    modelValue?: string[];
  }>(),
  {
    modelValue: () => [],
    limit: 5,
    maxSize: 10,
    acceptTypes:
      '.jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.txt,.xls,.xlsx,.ppt,.pptx,.csv,.md',
    disabled: false,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string[]];
  uploadError: [error: any];
  uploadSuccess: [file: FileItem];
}>();

const fileInputRef = ref<HTMLInputElement>();
const fileList = ref<FileItem[]>([]);
const uploadedUrls = ref<string[]>([]);
const showTooltip = ref(false);
const hideTimer = ref<NodeJS.Timeout | null>(null);
/** 上传中各文件的模拟进度计时器：上传成功、失败或组件卸载时都要清掉 */
const progressTimers = new Set<ReturnType<typeof setInterval>>();
const { httpRequest } = useUpload();

/** 监听 v-model 变化 */
watch(
  () => props.modelValue,
  (newVal) => {
    uploadedUrls.value = [...newVal];
    if (newVal.length === 0) {
      fileList.value = [];
    }
  },
  { immediate: true, deep: true },
);

/** 是否有文件 */
const hasFiles = computed(() => fileList.value.length > 0);

/** 是否达到上传限制 */
const isLimitReached = computed(() => fileList.value.length >= props.limit);

/** 触发文件选择 */
function triggerFileInput() {
  fileInputRef.value?.click();
}

/** 显示 tooltip */
function showTooltipHandler() {
  if (hideTimer.value) {
    clearTimeout(hideTimer.value);
    hideTimer.value = null;
  }
  showTooltip.value = true;
}

/** 隐藏 tooltip */
function hideTooltipHandler() {
  hideTimer.value = setTimeout(() => {
    showTooltip.value = false;
    hideTimer.value = null;
  }, 300);
}

/** 处理文件选择 */
async function handleFileSelect(event: Event) {
  const target = event.target as HTMLInputElement;
  const files = [...(target.files || [])];
  if (files.length === 0) {
    return;
  }

  if (files.length + fileList.value.length > props.limit) {
    message.error(`这次选的文件没有添加：最多添加 ${props.limit} 个附件。`);
    target.value = '';
    return;
  }

  for (const file of files) {
    if (file.size > props.maxSize * 1024 * 1024) {
      message.error(
        `「${file.name}」没有添加：单个附件不能超过 ${props.maxSize} MB。`,
      );
      continue;
    }

    const fileItem: FileItem = {
      name: file.name,
      size: file.size,
      uploading: true,
      progress: 0,
      raw: file,
    };
    fileList.value.push(fileItem);
    await uploadFile(fileItem);
  }

  target.value = '';
}

/** 停掉一个模拟进度计时器 */
function stopProgressTimer(timer: ReturnType<typeof setInterval>) {
  clearInterval(timer);
  progressTimers.delete(timer);
}

/** 上传文件 */
async function uploadFile(fileItem: FileItem) {
  const progressTimer = setInterval(() => {
    if (fileItem.progress! < 90) {
      fileItem.progress = (fileItem.progress || 0) + Math.random() * 10;
    }
  }, 100);
  progressTimers.add(progressTimer);
  try {
    // 失败提示由下面按文件名自己写，不再弹全局提示
    const response = await httpRequest(fileItem.raw!, undefined, {
      silentError: true,
    });
    stopProgressTimer(progressTimer);

    fileItem.uploading = false;
    fileItem.progress = 100;

    // 调试日志
    console.warn('上传响应:', response);

    // 兼容不同的返回格式：{ url: '...' } 或 { data: '...' } 或直接是字符串
    const fileUrl =
      (response as any)?.url || (response as any)?.data || response;
    fileItem.url = fileUrl;

    console.warn('提取的文件 URL:', fileUrl);

    // 只有当 URL 有效时才添加到列表
    if (fileUrl && typeof fileUrl === 'string') {
      uploadedUrls.value.push(fileUrl);
      emit('uploadSuccess', fileItem);
      updateModelValue();
    } else {
      throw new Error('服务器没有返回文件地址');
    }
  } catch (error) {
    fileItem.uploading = false;
    const reason = getRequestErrorReason(error);
    message.error(
      `「${fileItem.name}」没有上传成功${reason ? `：${reason}` : ''}。请重新添加。`,
    );
    emit('uploadError', error);

    const index = fileList.value.indexOf(fileItem);
    if (index !== -1) {
      removeFile(index);
    }
  } finally {
    // 成功时上面已经停掉；失败（包括没拿到文件地址）时在这里停掉
    stopProgressTimer(progressTimer);
  }
}

/** 删除文件 */
function removeFile(index: number) {
  const removedFile = fileList.value[index];
  fileList.value.splice(index, 1);
  if (removedFile?.url) {
    const urlIndex = uploadedUrls.value.indexOf(removedFile.url);
    if (urlIndex !== -1) {
      uploadedUrls.value.splice(urlIndex, 1);
    }
  }
  updateModelValue();
}

/** 更新 v-model */
function updateModelValue() {
  emit('update:modelValue', [...uploadedUrls.value]);
}

/** 清空文件 */
function clearFiles() {
  fileList.value = [];
  uploadedUrls.value = [];
  updateModelValue();
}

defineExpose({
  triggerFileInput,
  clearFiles,
});

onUnmounted(() => {
  if (hideTimer.value) {
    clearTimeout(hideTimer.value);
  }
  for (const timer of progressTimers) {
    clearInterval(timer);
  }
  progressTimers.clear();
});
</script>

<template>
  <div
    v-if="!disabled"
    class="chat-upload"
    @mouseenter="showTooltipHandler"
    @mouseleave="hideTooltipHandler"
  >
    <!-- 文件上传按钮 -->
    <button
      :aria-label="hasFiles ? `已添加 ${fileList.length} 个附件` : '添加附件'"
      :class="{ 'has-files': hasFiles }"
      :disabled="isLimitReached"
      :title="isLimitReached ? `最多添加 ${limit} 个附件` : '添加附件'"
      class="chat-upload__trigger"
      type="button"
      @click="triggerFileInput"
    >
      <IconifyIcon icon="lucide:paperclip" :size="16" />
      <!-- 文件数量 -->
      <span v-if="hasFiles" class="chat-upload__count">
        {{ fileList.length }}
      </span>
    </button>

    <!-- 隐藏的文件输入框 -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      style="display: none"
      :accept="acceptTypes"
      @change="handleFileSelect"
    />

    <!-- 悬停显示的文件列表 -->
    <div
      v-if="hasFiles && showTooltip"
      class="chat-upload__popover"
      @mouseenter="showTooltipHandler"
      @mouseleave="hideTooltipHandler"
    >
      <div class="chat-upload__list">
        <div
          v-for="(file, index) in fileList"
          :key="index"
          :class="{ 'is-uploading': file.uploading }"
          class="chat-upload__file"
        >
          <IconifyIcon
            :icon="getFileIcon(file.name)"
            class="chat-upload__file-icon"
          />
          <span class="chat-upload__file-name">{{ file.name }}</span>
          <span class="chat-upload__file-size">
            {{ formatFileSize(file.size) }}
          </span>
          <div
            v-if="file.uploading"
            :aria-label="`正在上传 ${file.name}`"
            class="chat-upload__progress"
            role="progressbar"
          >
            <div
              class="chat-upload__progress-bar"
              :style="{ width: `${file.progress || 0}%` }"
            ></div>
          </div>
          <button
            v-else-if="!disabled"
            :aria-label="`移除 ${file.name}`"
            class="chat-upload__remove"
            type="button"
            @click="removeFile(index)"
          >
            <IconifyIcon icon="lucide:x" :size="12" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 附件按钮与悬停列表（V1.0.1.6 界面规范）：颜色全部取 token，浮层用浮层底与浮层阴影 token */
.chat-upload {
  position: relative;
  display: inline-block;
}

.chat-upload__trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--nus-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.chat-upload__trigger:hover:not(:disabled) {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.chat-upload__trigger.has-files {
  color: var(--nus-primary);
}

.chat-upload__trigger:disabled {
  color: var(--nus-text-disabled);
  cursor: not-allowed;
}

.chat-upload__count {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-medium);
  line-height: 16px;
  color: var(--nus-primary);
  text-align: center;
  background: var(--nus-bg-selected);
  border-radius: var(--nus-radius-sm);
}

.chat-upload__popover {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  z-index: 1000;
  min-width: 240px;
  max-width: 320px;
  padding: var(--nus-space-1);
  background: var(--nus-bg-elevated);
  border-radius: var(--nus-radius-lg);
  box-shadow: var(--nus-shadow-popover);
}

.chat-upload__list {
  max-height: 200px;
  overflow-y: auto;
}

.chat-upload__file {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  padding: 6px var(--nus-space-2);
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.chat-upload__file:hover {
  background: var(--nus-bg-hover);
}

.chat-upload__file.is-uploading {
  opacity: 0.7;
}

.chat-upload__file-icon {
  flex: none;
  color: var(--nus-text-tertiary);
}

.chat-upload__file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--nus-text);
  white-space: nowrap;
}

.chat-upload__file-size {
  flex: none;
  color: var(--nus-text-tertiary);
}

.chat-upload__progress {
  flex: none;
  width: 60px;
  height: 4px;
  overflow: hidden;
  background: var(--nus-border-subtle);
  border-radius: var(--nus-radius-sm);
}

.chat-upload__progress-bar {
  height: 100%;
  background: var(--nus-primary);
  transition: width var(--nus-dur) var(--nus-ease);
}

.chat-upload__remove {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  color: var(--nus-text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--nus-radius-sm);
  transition: var(--nus-transition-colors);
}

.chat-upload__remove:hover {
  color: var(--nus-danger);
  background: var(--nus-danger-tint);
}
</style>
