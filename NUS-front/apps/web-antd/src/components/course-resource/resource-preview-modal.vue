<script lang="ts" setup>
import type { CourseResourceLike } from './resource-helpers';

import { computed, onUnmounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Modal, Spin } from 'ant-design-vue';

import { getCourseResourcePreviewText } from '#/api/edu/course-resource';
import { EmptyState } from '#/components/empty-state';

import {
  buildOfficePreviewUrl,
  canUseOfficeViewer,
  formatResourceSize,
  getResourceKindIcon,
  getResourceKindLabel,
  resolveResourceKind,
  triggerResourceDownload,
} from './resource-helpers';

const props = defineProps<{
  downloadResource?: (resource: CourseResourceLike) => Promise<void>;
  fetchResource?: (resource: CourseResourceLike) => Promise<Blob>;
  open: boolean;
  resource?: CourseResourceLike | null;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

const kind = computed(() => resolveResourceKind(props.resource || {}));
const fetchedResourceUrl = ref('');
const resourceUrl = computed(
  () =>
    fetchedResourceUrl.value || String(props.resource?.fileUrl || '').trim(),
);
const resourceLoading = ref(false);
const textPreview = ref('');
const textPreviewLoading = ref(false);
let loadVersion = 0;
const officePreviewUrl = computed(() =>
  kind.value === 'excel' || kind.value === 'powerpoint' || kind.value === 'word'
    ? buildOfficePreviewUrl(resourceUrl.value)
    : '',
);
const useTextPreviewFallback = computed(
  () =>
    props.open &&
    Boolean(props.resource?.id) &&
    !props.fetchResource &&
    (kind.value === 'word' || kind.value === 'powerpoint') &&
    !canUseOfficeViewer(resourceUrl.value),
);
const canRenderPreview = computed(() => {
  if (!resourceUrl.value) return false;
  if (useTextPreviewFallback.value) return true;
  return (
    kind.value === 'audio' ||
    kind.value === 'image' ||
    kind.value === 'pdf' ||
    kind.value === 'text' ||
    kind.value === 'video' ||
    (kind.value === 'word' && canUseOfficeViewer(resourceUrl.value)) ||
    (kind.value === 'powerpoint' && canUseOfficeViewer(resourceUrl.value)) ||
    (kind.value === 'excel' && canUseOfficeViewer(resourceUrl.value))
  );
});
const previewHint = computed(() => {
  if (!resourceUrl.value) return '资源地址缺失，无法预览。';
  if (
    kind.value === 'word' ||
    kind.value === 'powerpoint' ||
    kind.value === 'excel'
  ) {
    if (useTextPreviewFallback.value) {
      return textPreview.value
        ? '当前为本地文档文本预览模式，适合快速查看主要内容。'
        : '当前文档地址不是公网可访问链接，已尝试使用本地文本预览；若内容为空，请改用新标签页或下载查看。';
    }
    if (!canUseOfficeViewer(resourceUrl.value)) {
      return '当前文档地址不是公网可访问链接，无法在内嵌预览器中打开，请使用新标签页或下载查看。';
    }
    return 'Office 文档通过在线预览器加载，若空白可改用下载查看。';
  }
  return '若预览区加载失败，可改用新标签页打开或下载查看。';
});

function handleClose() {
  emit('update:open', false);
}

function handleOpenNewTab() {
  if (!resourceUrl.value) return;
  window.open(resourceUrl.value, '_blank', 'noopener');
}

async function handleDownload() {
  if (!props.resource) return;
  if (props.downloadResource) {
    await props.downloadResource(props.resource);
    return;
  }
  triggerResourceDownload(props.resource);
}

function releaseFetchedResourceUrl() {
  if (!fetchedResourceUrl.value) return;
  URL.revokeObjectURL(fetchedResourceUrl.value);
  fetchedResourceUrl.value = '';
}

watch(
  () =>
    [
      props.open,
      props.resource?.id,
      kind.value,
      props.resource?.fileUrl,
    ] as const,
  async ([open, resourceId]) => {
    const version = ++loadVersion;
    releaseFetchedResourceUrl();
    textPreview.value = '';
    if (open && props.resource && props.fetchResource) {
      resourceLoading.value = true;
      try {
        const blob = await props.fetchResource(props.resource);
        if (version === loadVersion) {
          fetchedResourceUrl.value = URL.createObjectURL(blob);
        }
      } catch {
        // 读取失败的原因已由全局请求提示；这里保持预览区的兜底（原始地址或「无法预览」说明）
      } finally {
        if (version === loadVersion) resourceLoading.value = false;
      }
      return;
    }
    resourceLoading.value = false;
    if (!open || !resourceId || !useTextPreviewFallback.value) {
      textPreviewLoading.value = false;
      return;
    }
    textPreviewLoading.value = true;
    try {
      textPreview.value = String(
        (await getCourseResourcePreviewText(resourceId)) || '',
      ).trim();
    } finally {
      textPreviewLoading.value = false;
    }
  },
  { immediate: true },
);

onUnmounted(() => {
  loadVersion++;
  releaseFetchedResourceUrl();
});
</script>

<template>
  <Modal
    :footer="null"
    :open="open"
    :title="resource?.resourceName || '资源预览'"
    :width="1200"
    centered
    destroy-on-close
    @cancel="handleClose"
    @update:open="emit('update:open', $event)"
  >
    <div class="resource-preview">
      <div class="resource-preview__bar">
        <div class="resource-preview__meta">
          <span class="resource-preview__kind">
            <IconifyIcon
              :icon="getResourceKindIcon(kind)"
              aria-hidden="true"
              class="resource-preview__icon"
            />
            {{ getResourceKindLabel(kind) }}
          </span>
          <span>大小：{{ formatResourceSize(resource?.fileSize) }}</span>
          <span v-if="resource?.createTime">
            上传于：{{ resource.createTime }}
          </span>
        </div>
        <div class="resource-preview__actions">
          <Button @click="handleOpenNewTab">新标签页打开</Button>
          <Button type="primary" @click="handleDownload">下载文件</Button>
        </div>
      </div>

      <div v-if="resourceLoading" class="resource-preview__stage is-center">
        <Spin tip="正在读取资源文件…" />
      </div>
      <div v-else-if="canRenderPreview" class="resource-preview__stage">
        <div v-if="useTextPreviewFallback" class="resource-preview__text">
          <div v-if="textPreviewLoading" class="resource-preview__text-state">
            <Spin tip="正在提取文档文字…" />
          </div>
          <pre v-else-if="textPreview" class="resource-preview__pre">{{
            textPreview
          }}</pre>
          <div v-else class="resource-preview__text-state">
            <EmptyState
              description="可以在新标签页打开，或下载后查看。"
              title="没有提取到可显示的文字"
            />
          </div>
        </div>
        <img
          v-else-if="kind === 'image'"
          :alt="resource?.resourceName || '图片资源'"
          :src="resourceUrl"
          class="resource-preview__image"
        />
        <video
          v-else-if="kind === 'video'"
          :src="resourceUrl"
          class="resource-preview__media"
          controls
          playsinline
        ></video>
        <audio
          v-else-if="kind === 'audio'"
          :src="resourceUrl"
          class="resource-preview__audio"
          controls
        ></audio>
        <iframe
          v-else-if="kind === 'pdf' || kind === 'text'"
          :src="resourceUrl"
          :title="resource?.resourceName || '资源预览'"
          class="resource-preview__frame"
        ></iframe>
        <iframe
          v-else
          :src="officePreviewUrl"
          :title="resource?.resourceName || '文档预览'"
          class="resource-preview__frame"
        ></iframe>
      </div>

      <div v-else class="resource-preview__stage is-center">
        <EmptyState
          :description="previewHint"
          kind="blocked"
          title="无法在页面里预览"
        />
      </div>

      <p
        v-if="!resourceLoading && canRenderPreview"
        class="resource-preview__hint"
      >
        {{ previewHint }}
      </p>
    </div>
  </Modal>
</template>

<style scoped>
.resource-preview {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
}

.resource-preview__bar {
  /* 资源信息与操作：一行文字，不再套渐变卡片 */
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
}

.resource-preview__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-1) var(--nus-space-3);
  align-items: center;
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.resource-preview__kind {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text-secondary);
}

.resource-preview__icon {
  width: 16px;
  height: 16px;
}

.resource-preview__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
}

.resource-preview__stage {
  /* 预览区：凹陷底、淡边框、圆角 8 */
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 68vh;
  overflow: hidden;
  background: var(--nus-bg-sunken);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.resource-preview__frame,
.resource-preview__media,
.resource-preview__image {
  width: 100%;
  height: 68vh;
  border: 0;
}

.resource-preview__image {
  object-fit: contain;
}

.resource-preview__media {
  background: var(--nus-bg-canvas);
}

.resource-preview__audio {
  width: min(640px, calc(100% - 48px));
}

.resource-preview__text {
  width: 100%;
  height: 68vh;
  padding: var(--nus-space-6);
  overflow: auto;
  background: var(--nus-bg-surface);
}

.resource-preview__text-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.resource-preview__pre {
  margin: 0;
  font-family: var(--nus-font-sans);
  font-size: var(--nus-fs-body);
  line-height: 1.8;
  color: var(--nus-text);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.resource-preview__hint {
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

@media (width <= 768px) {
  .resource-preview__stage {
    min-height: 56vh;
  }

  .resource-preview__frame,
  .resource-preview__media,
  .resource-preview__image,
  .resource-preview__text {
    height: 56vh;
  }
}
</style>
