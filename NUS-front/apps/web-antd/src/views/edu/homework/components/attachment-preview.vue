<script lang="ts" setup>
import { computed, onUnmounted, ref, watch } from 'vue';

import { Button, Image, Spin } from 'ant-design-vue';

import { downloadHomeworkAttachment } from '#/api/edu/student/homework';
import { EmptyState } from '#/components/empty-state';

/**
 * 提交附件（V1.0.1.6 界面规范）：按图片、音频、视频、其他文件分组；读取中写明在读什么，读取失败说明下一步。
 * 分组之间只用间距与小标题，不再一项一张卡片。
 */
const props = defineProps<{
  attachmentUrls?: string | string[];
  title?: string;
}>();

interface AttachmentItem {
  kind: 'audio' | 'image' | 'other' | 'video';
  name: string;
  url: string;
  sourceUrl: string;
}

function handleOpen(url: string) {
  window.open(url, '_blank');
}

function parseUrls(value?: string | string[]) {
  if (!value) return [];
  if (Array.isArray(value)) {
    return value.filter((item) => typeof item === 'string' && item.trim());
  }
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed.filter((item) => typeof item === 'string' && item.trim());
    }
  } catch {
    // ignore
  }
  return [];
}

function getName(url: string) {
  try {
    const lastPart = new URL(url, window.location.origin).pathname
      .split('/')
      .findLast(Boolean);
    return lastPart ? decodeURIComponent(lastPart) : '附件';
  } catch {
    const lastPart = url.split('/').findLast(Boolean);
    return lastPart ? decodeURIComponent(lastPart) : '附件';
  }
}

function getKind(url: string): AttachmentItem['kind'] {
  const lower = url.toLowerCase();
  if (/\.(?:png|jpe?g|gif|bmp|webp|svg)(?:\?|$)/.test(lower)) return 'image';
  if (/\.(?:mp3|wav|m4a|aac|amr|ogg)(?:\?|$)/.test(lower)) return 'audio';
  if (/\.(?:mp4|mov|m4v|avi|webm|mkv)(?:\?|$)/.test(lower)) return 'video';
  return 'other';
}

const items = ref<AttachmentItem[]>([]);
const loading = ref(false);
const loadError = ref(false);
let loadRequestId = 0;

function releaseObjectUrls() {
  items.value.forEach((item) => URL.revokeObjectURL(item.url));
}

watch(
  () => props.attachmentUrls,
  async (value) => {
    const requestId = ++loadRequestId;
    releaseObjectUrls();
    items.value = [];
    loadError.value = false;
    const sourceUrls = parseUrls(value);
    loading.value = sourceUrls.length > 0;
    try {
      const loaded = await Promise.all(
        sourceUrls.map(async (sourceUrl) => {
          const blob = await downloadHomeworkAttachment(sourceUrl);
          return {
            sourceUrl,
            url: URL.createObjectURL(blob),
            name: getName(sourceUrl),
            kind: getKind(sourceUrl),
          } as AttachmentItem;
        }),
      );
      if (requestId === loadRequestId) {
        items.value = loaded;
      } else {
        loaded.forEach((item) => URL.revokeObjectURL(item.url));
      }
    } catch {
      if (requestId === loadRequestId) loadError.value = true;
    } finally {
      if (requestId === loadRequestId) loading.value = false;
    }
  },
  { immediate: true },
);

onUnmounted(releaseObjectUrls);

const imageItems = computed(() =>
  items.value.filter((item) => item.kind === 'image'),
);
const audioItems = computed(() =>
  items.value.filter((item) => item.kind === 'audio'),
);
const videoItems = computed(() =>
  items.value.filter((item) => item.kind === 'video'),
);
const otherItems = computed(() =>
  items.value.filter((item) => item.kind === 'other'),
);
</script>

<template>
  <div class="attachments">
    <div class="attachments__header">
      <span class="attachments__title">{{ title || '提交附件' }}</span>
      <span v-if="!loading && items.length > 0" class="attachments__count">
        共 {{ items.length }} 个
      </span>
    </div>

    <div v-if="loading" class="attachments__note" role="status">
      <Spin size="small" />
      <span>正在读取附件…</span>
    </div>
    <p v-else-if="loadError" class="attachments__note">
      附件没有读取成功，请刷新页面后重试。
    </p>
    <EmptyState v-else-if="items.length === 0" compact title="没有提交附件" />

    <div v-else class="attachments__body">
      <section v-if="imageItems.length > 0" class="attachments__section">
        <h4 class="attachments__section-title">图片附件</h4>
        <div class="attachments__grid">
          <figure
            v-for="item in imageItems"
            :key="item.url"
            class="attachments__figure"
          >
            <Image :src="item.url" class="attachments__image" />
            <figcaption class="attachments__name">{{ item.name }}</figcaption>
          </figure>
        </div>
      </section>

      <section v-if="audioItems.length > 0" class="attachments__section">
        <h4 class="attachments__section-title">音频附件</h4>
        <div
          v-for="item in audioItems"
          :key="item.url"
          class="attachments__media"
        >
          <div class="attachments__name">{{ item.name }}</div>
          <audio
            :src="item.url"
            class="attachments__audio"
            controls
            preload="metadata"
          ></audio>
        </div>
      </section>

      <section v-if="videoItems.length > 0" class="attachments__section">
        <h4 class="attachments__section-title">视频附件</h4>
        <div
          v-for="item in videoItems"
          :key="item.url"
          class="attachments__media"
        >
          <div class="attachments__name">{{ item.name }}</div>
          <video
            :src="item.url"
            class="attachments__video"
            controls
            preload="metadata"
          ></video>
        </div>
      </section>

      <section v-if="otherItems.length > 0" class="attachments__section">
        <h4 class="attachments__section-title">其他附件</h4>
        <ul class="attachments__files">
          <li
            v-for="item in otherItems"
            :key="item.url"
            class="attachments__file"
          >
            <span class="attachments__name">{{ item.name }}</span>
            <Button size="small" type="link" @click="handleOpen(item.url)">
              打开
            </Button>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.attachments {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
}

.attachments__header {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
}

/* 标题按区块标签写：13/500 次要色，与使用它的页面里的小节标签一致 */
.attachments__title {
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.attachments__count {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.attachments__note {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.attachments__body {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
}

.attachments__section {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
}

.attachments__section-title {
  margin: 0;
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.attachments__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--nus-space-3);
}

.attachments__figure {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
  min-width: 0;
  margin: 0;
}

.attachments__image,
.attachments__video {
  width: 100%;
  overflow: hidden;
  border-radius: var(--nus-radius);
}

.attachments__image :deep(img) {
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius);
}

/* 音频、视频放在凹陷块里，名字在上 */
.attachments__media {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
  padding: var(--nus-space-3);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.attachments__audio {
  width: 100%;
}

.attachments__files {
  padding: 0;
  margin: 0;
  list-style: none;
}

.attachments__file {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--nus-space-1) 0;
}

.attachments__file + .attachments__file {
  border-top: 1px solid var(--nus-border-subtle);
}

.attachments__name {
  min-width: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}
</style>
