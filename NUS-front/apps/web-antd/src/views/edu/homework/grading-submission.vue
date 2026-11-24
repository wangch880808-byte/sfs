<script lang="ts" setup>
import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { computed, onBeforeUnmount,ref, watch } from 'vue';

import { ChevronRight, ExternalLink, Eye } from '@vben/icons';

import { Button, Modal } from 'ant-design-vue';

import { getHomeworkRecordHistory } from '#/api/edu/homework-record';
import { downloadHomeworkAttachment } from '#/api/edu/student/homework';
import { isRichHtmlEmpty, RichHtml } from '#/components/rich-html';
import HomeworkHistoryTimeline from '#/views/edu/homework/components/homework-history-timeline.vue';

/**
 * 批改工作台右栏的“提交内容”一列：成绩轨迹（按需展开）、任务要求、作答内容与附件预览。
 * reloadToken 变化（保存、撤销之后）时重读已展开的成绩轨迹。
 */
const props = defineProps<{
  historyOpen: boolean;
  record: EduHomeworkRecordApi.HomeworkRecord;
  reloadToken: number;
  requirement: string;
}>();

interface AttachmentItem {
  kind: 'audio' | 'image' | 'other' | 'video';
  name: string;
  url: string;
  sourceUrl?: string;
}

const requirementOpen = ref(false);
const historyLoading = ref(false);
const historyError = ref(false);
const historyItems = ref<EduHomeworkRecordApi.HistoryItem[]>([]);
let historyRequestId = 0;
const previewOpen = ref(false);
const activeAttachment = ref<AttachmentItem>();
/** 正在读取、准备预览的附件 */
const previewingUrl = ref<string>();
const previewError = ref('');
const thumbUrls = ref<Record<string, string>>({});

async function loadThumb(item: AttachmentItem) {
  if (item.kind !== 'image' || thumbUrls.value[item.url]) return;
  try {
    const blob = await downloadHomeworkAttachment(item.url);
    thumbUrls.value[item.url] = URL.createObjectURL(blob);
  } catch {

  }
}

function parseAttachmentUrls(value?: string | string[]) {
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
    // 解析不了按没有附件处理
  }
  return [];
}

function getAttachmentName(url: string) {
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

function getAttachmentKind(url: string): AttachmentItem['kind'] {
  const lower = url.toLowerCase();
  if (/\.(?:png|jpe?g|gif|bmp|webp|svg)(?:\?|$)/.test(lower)) return 'image';
  if (/\.(?:mp3|wav|m4a|aac|amr|ogg)(?:\?|$)/.test(lower)) return 'audio';
  if (/\.(?:mp4|mov|m4v|avi|webm|mkv)(?:\?|$)/.test(lower)) return 'video';
  return 'other';
}

const KIND_LABEL: Record<AttachmentItem['kind'], string> = {
  audio: '音频',
  image: '图片',
  other: '文件',
  video: '视频',
};

const attachments = computed<AttachmentItem[]>(() =>
  parseAttachmentUrls(props.record.attachmentUrls).map((url) => ({
    kind: getAttachmentKind(url),
    name: getAttachmentName(url),
    url,
  })),
);
/** 编辑器留下的空段落（<p><br></p>）也按没填写处理 */
const hasRequirement = computed(() => !isRichHtmlEmpty(props.requirement));
const hasAnswerContent = computed(() => !isRichHtmlEmpty(props.record.content));

watch(attachments, (list) => list.forEach(loadThumb), { immediate: true });

onBeforeUnmount(() => {
  Object.values(thumbUrls.value).forEach((url) => URL.revokeObjectURL(url));
});

async function loadHistory() {
  const recordId = props.record.id;
  if (!recordId) return;
  const requestId = ++historyRequestId;
  historyLoading.value = true;
  historyError.value = false;
  historyItems.value = [];
  try {
    const items = await getHomeworkRecordHistory(recordId, {
      silentError: true,
    });
    if (requestId === historyRequestId) historyItems.value = items ?? [];
  } catch {
    if (requestId === historyRequestId) historyError.value = true;
  } finally {
    if (requestId === historyRequestId) historyLoading.value = false;
  }
}

watch(
  () => [props.historyOpen, props.record.id, props.reloadToken] as const,
  ([open]) => {
    ++historyRequestId;
    historyItems.value = [];
    historyLoading.value = false;
    historyError.value = false;
    if (open) void loadHistory();
  },
  { immediate: true },
);

function revokeActive() {
  if (activeAttachment.value?.url.startsWith('blob:')) {
    URL.revokeObjectURL(activeAttachment.value.url);
  }
}

async function handlePreview(item: AttachmentItem) {
  previewingUrl.value = item.url;
  previewError.value = '';
  try {
    const blob = await downloadHomeworkAttachment(item.url);
    revokeActive();
    activeAttachment.value = {
      ...item,
      sourceUrl: item.url,
      url: URL.createObjectURL(blob),
    };
    previewOpen.value = true;
  } catch {
    previewError.value = `「${item.name}」没有打开：读取附件失败，请稍后重试。`;
  } finally {
    if (previewingUrl.value === item.url) previewingUrl.value = undefined;
  }
}

function handleOpenInNewTab() {
  if (activeAttachment.value?.url) {
    window.open(activeAttachment.value.url, '_blank');
  }
}

function closePreview() {
  revokeActive();
  activeAttachment.value = undefined;
}
</script>

<template>
  <div class="grading-detail__submission">
    <section v-if="historyOpen" class="grading-section">
      <h3 class="grading-section__title">成绩轨迹</h3>
      <HomeworkHistoryTimeline
        :error="historyError"
        :items="historyItems"
        :loading="historyLoading"
        @retry="loadHistory"
      />
    </section>

    <section v-if="hasRequirement" class="grading-section">
      <button
        :aria-expanded="requirementOpen"
        class="grading-disclosure"
        type="button"
        @click="requirementOpen = !requirementOpen"
      >
        <ChevronRight
          :class="{ 'is-open': requirementOpen }"
          aria-hidden="true"
          class="grading-disclosure__icon"
        />
        任务要求
      </button>
      <RichHtml
        v-if="requirementOpen"
        :html="requirement"
        :max-height="320"
        class="grading-sunken"
      />
    </section>

    <section class="grading-section">
      <h3 class="grading-section__title">提交内容</h3>
      <RichHtml v-if="hasAnswerContent" :html="record.content" />
      <p v-else class="grading-note">没有填写文字内容。</p>
    </section>

    <section class="grading-section">
      <h3 class="grading-section__title">
        附件
        <span v-if="attachments.length > 0" class="grading-section__count">
          {{ attachments.length }}
        </span>
      </h3>
      <ul v-if="attachments.length > 0" class="grading-files">
        <li v-for="item in attachments" :key="item.url">
          <button
            class="grading-file"
            type="button"
            @click="handlePreview(item)"
          >
            <img
              v-if="item.kind === 'image' && thumbUrls[item.url]"
              :alt="item.name"
              :src="thumbUrls[item.url]"
              class="grading-file__thumb"
            />
            <span class="grading-file__name">{{ item.name }}</span>
            <span class="grading-file__kind">{{ KIND_LABEL[item.kind] }}</span>
            <span class="grading-file__action">
              <Eye aria-hidden="true" class="size-4" />
              {{ previewingUrl === item.url ? '正在打开…' : '预览' }}
            </span>
          </button>
        </li>
      </ul>
      <p v-else class="grading-note">没有提交附件。</p>
      <p v-if="previewError" class="grading-note" role="alert">
        {{ previewError }}
      </p>
    </section>

    <Modal
      v-model:open="previewOpen"
      :footer="null"
      :title="activeAttachment?.name || '附件预览'"
      width="min(960px, calc(100vw - 32px))"
      @cancel="closePreview"
    >
      <div v-if="activeAttachment" class="grading-preview">
        <div class="grading-preview__bar">
          <span class="grading-preview__kind">
            {{ KIND_LABEL[activeAttachment.kind] }}
          </span>
          <Button size="small" @click="handleOpenInNewTab">
            <template #icon>
              <ExternalLink aria-hidden="true" class="size-4" />
            </template>
            新窗口打开
          </Button>
        </div>
        <img
          v-if="activeAttachment.kind === 'image'"
          :alt="activeAttachment.name"
          :src="activeAttachment.url"
          class="grading-preview__media grading-preview__media--fit"
        />
        <audio
          v-else-if="activeAttachment.kind === 'audio'"
          :src="activeAttachment.url"
          autoplay
          class="grading-preview__audio"
          controls
        ></audio>
        <video
          v-else-if="activeAttachment.kind === 'video'"
          :src="activeAttachment.url"
          autoplay
          class="grading-preview__media grading-preview__media--fit"
          controls
        ></video>
        <iframe
          v-else
          :src="activeAttachment.url"
          class="grading-preview__media grading-preview__media--frame"
          title="附件预览"
        ></iframe>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.grading-disclosure {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
  align-self: flex-start;
  padding: 2px var(--nus-space-2) 2px 2px;
  margin-left: -2px;
  font: inherit;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: var(--nus-radius-sm);
  transition: var(--nus-transition-colors);
}

.grading-disclosure:hover {
  background: var(--nus-bg-hover);
}

.grading-file__thumb {
  flex: none;
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: var(--nus-radius-sm);
}

.grading-disclosure:focus-visible,
.grading-file:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 2px;
}

.grading-disclosure__icon {
  width: 16px;
  height: 16px;
  color: var(--nus-text-tertiary);
  transition: transform var(--nus-dur) var(--nus-ease);
}

.grading-disclosure__icon.is-open {
  transform: rotate(90deg);
}

/* ---- 附件：一行一个，悬停有底色 ---- */
.grading-files {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  margin: 0 calc(var(--nus-space-2) * -1);
  list-style: none;
}

.grading-file {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  width: 100%;
  padding: var(--nus-space-2);
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.grading-file:hover {
  background: var(--nus-bg-hover);
}

.grading-file__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: nowrap;
}

.grading-file__kind {
  flex: none;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.grading-file__action {
  display: inline-flex;
  flex: none;
  gap: var(--nus-space-1);
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-primary);
}

/* ---- 附件预览弹窗 ---- */
.grading-preview {
  min-height: 160px;
}

.grading-preview__bar {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--nus-space-3);
}

.grading-preview__kind {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.grading-preview__media {
  display: block;
  width: 100%;
  background: var(--nus-bg-sunken);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.grading-preview__media--fit {
  max-height: 68vh;
  object-fit: contain;
}

.grading-preview__media--frame {
  height: 68vh;
}

.grading-preview__audio {
  width: 100%;
}
</style>
