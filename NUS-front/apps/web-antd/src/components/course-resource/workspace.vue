<script lang="ts" setup>
import type { UploadProps } from 'ant-design-vue';
import type { UploadRequestOption } from 'ant-design-vue/lib/vc-upload/interface';

import type { CourseResourceLike, ResourceKind } from './resource-helpers';

import type { EduCourseResourceApi } from '#/api/edu/course-resource';

import { computed, ref, watch } from 'vue';

import { confirm } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  message,
  Modal,
  Pagination,
  Progress,
  Select,
  Upload,
} from 'ant-design-vue';

import {
  deleteCourseResource,
  uploadCourseResource,
} from '#/api/edu/course-resource';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getRequestErrorReason } from '#/utils/request-error';
import { getResourceUploadStatusMeta } from '#/utils/status-course-detail';

import {
  canPreviewResource,
  COURSE_RESOURCE_ACCEPT,
  COURSE_RESOURCE_MAX_SIZE,
  formatResourceSize,
  getPreviewActionLabel,
  getResourceKindIcon,
  getResourceKindLabel,
  resolveResourceKind,
  triggerResourceDownload,
} from './resource-helpers';
import ResourcePreviewModal from './resource-preview-modal.vue';

interface UploadTask {
  error?: string;
  fileName: string;
  percent: number;
  status: 'error' | 'success' | 'uploading';
  uid: string;
}

const props = withDefaults(
  defineProps<{
    courseId?: number;
    downloadResource?: (resource: CourseResourceLike) => Promise<void>;
    editable?: boolean;
    emptyDescription?: string;
    fetchResource?: (resource: CourseResourceLike) => Promise<Blob>;
    resources: CourseResourceLike[];
  }>(),
  {
    courseId: undefined,
    downloadResource: undefined,
    editable: false,
    emptyDescription: '暂无资源',
    fetchResource: undefined,
  },
);

const emit = defineEmits<{
  updated: [];
}>();

const keyword = ref('');
const kindFilter = ref<'all' | ResourceKind>('all');
const previewOpen = ref(false);
const previewResource = ref<CourseResourceLike | null>(null);
const uploadModalOpen = ref(false);
const uploadTasks = ref<UploadTask[]>([]);
const pageNo = ref(1);
const pageSize = ref(8);

const kindOptions = computed(() => {
  const kinds = new Set<ResourceKind>();
  props.resources.forEach((resource) =>
    kinds.add(resolveResourceKind(resource)),
  );
  return [
    { label: '全部类型', value: 'all' as const },
    ...[...kinds].map((kind) => ({
      label: getResourceKindLabel(kind),
      value: kind,
    })),
  ];
});

const filteredResources = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return [...props.resources]
    .filter((resource) => {
      const matchesKeyword = normalizedKeyword
        ? String(resource.resourceName || '')
            .toLowerCase()
            .includes(normalizedKeyword)
        : true;
      const currentKind = resolveResourceKind(resource);
      const matchesKind =
        kindFilter.value === 'all' ? true : currentKind === kindFilter.value;
      return matchesKeyword && matchesKind;
    })
    .toSorted((left, right) => {
      const leftTime = new Date(String(left.createTime || '')).getTime() || 0;
      const rightTime = new Date(String(right.createTime || '')).getTime() || 0;
      return rightTime - leftTime;
    });
});
const pagedResources = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value;
  return filteredResources.value.slice(start, start + pageSize.value);
});

/** 工具条右侧的数量说明：上传中优先，其次是筛选结果 */
const toolbarSummary = computed(() => {
  const pending = uploadTasks.value.filter(
    (task) => task.status === 'uploading',
  ).length;
  if (pending > 0) return `正在上传 ${pending} 个文件…`;
  const total = props.resources.length;
  const visible = filteredResources.value.length;
  if (visible < total) return `找到 ${visible} 个，共 ${total} 个资源`;
  return `共 ${total} 个资源`;
});

function isUploadEnabled() {
  return Boolean(props.editable && props.courseId);
}

function upsertUploadTask(task: UploadTask) {
  const index = uploadTasks.value.findIndex((item) => item.uid === task.uid);
  if (index !== -1) {
    uploadTasks.value[index] = task;
    return;
  }
  uploadTasks.value.unshift(task);
}

function handleOpenUploadModal() {
  if (!isUploadEnabled()) {
    message.warning('当前课程不可上传资源');
    return;
  }
  uploadTasks.value = [];
  uploadModalOpen.value = true;
}

function handleBeforeUpload(file: File) {
  if (!isUploadEnabled()) {
    message.warning('当前课程不可上传资源');
    return Upload.LIST_IGNORE;
  }
  if (file.size > COURSE_RESOURCE_MAX_SIZE) {
    message.warning(`「${file.name}」没有上传：单个文件不能超过 500 MB。`);
    return Upload.LIST_IGNORE;
  }
  return true;
}

async function handleCustomRequest(options: UploadRequestOption) {
  if (!isUploadEnabled() || !props.courseId) {
    const error = new Error('课程资源上传未启用');
    options.onError?.(error);
    return;
  }

  const file = options.file as File;
  const uid = String(
    (options.file as any)?.uid || `${Date.now()}-${file.name}`,
  );

  upsertUploadTask({
    fileName: file.name,
    percent: 0,
    status: 'uploading',
    uid,
  });

  try {
    // 失败提示由下面按文件名自己写，不再弹全局提示（同时上传多个文件时要说清是哪一个）
    await uploadCourseResource(
      props.courseId,
      file,
      undefined,
      (event) => {
        const eventTotal = Number(event?.total);
        const total = eventTotal > 0 ? eventTotal : file.size;
        const loaded = Number(event?.loaded || 0);
        const percent =
          total > 0 ? Math.min(100, Math.round((loaded / total) * 100)) : 0;
        upsertUploadTask({
          fileName: file.name,
          percent,
          status: 'uploading',
          uid,
        });
        options.onProgress?.({ percent });
      },
      { silentError: true },
    );

    upsertUploadTask({
      fileName: file.name,
      percent: 100,
      status: 'success',
      uid,
    });
    options.onSuccess?.(file);
    message.success(`已上传「${file.name}」`);
    emit('updated');
  } catch (error: any) {
    const errorMessage = getRequestErrorReason(error) || '原因未知';
    upsertUploadTask({
      error: errorMessage,
      fileName: file.name,
      percent: 0,
      status: 'error',
      uid,
    });
    options.onError?.(error);
    message.error(
      `「${file.name}」没有上传成功：${errorMessage}。可以重新上传。`,
    );
  }
}

function handleOpenPreview(resource: CourseResourceLike) {
  previewResource.value = resource;
  previewOpen.value = true;
}

async function handleDownload(resource: CourseResourceLike) {
  if (props.downloadResource) {
    await props.downloadResource(resource);
    return;
  }
  if (!triggerResourceDownload(resource)) {
    message.warning('资源地址缺失，暂时无法下载');
  }
}

async function handleDelete(resource: EduCourseResourceApi.CourseResource) {
  if (!props.editable || !resource.id) return;
  await confirm({
    cancelText: '返回',
    confirmText: '删除',
    content: '删除后学生不能再查看或下载这个资源；需要时可以重新上传。',
    icon: 'warning',
    title: `删除资源「${resource.resourceName}」？`,
  });
  await deleteCourseResource(resource.id);
  message.success('资源已删除');
  emit('updated');
}

function getActionLabel(resource: CourseResourceLike) {
  return canPreviewResource(resource)
    ? getPreviewActionLabel(resource)
    : '打开文件';
}

function handlePrimaryAction(resource: CourseResourceLike) {
  if (canPreviewResource(resource)) {
    handleOpenPreview(resource);
    return;
  }
  if (props.downloadResource) {
    void handleDownload(resource);
    return;
  }
  const fileUrl = String(resource.fileUrl || '').trim();
  if (!fileUrl) {
    message.warning('资源地址缺失，暂时无法打开');
    return;
  }
  window.open(fileUrl, '_blank', 'noopener');
}

function handlePageChange(page: number, size: number) {
  pageNo.value = page;
  pageSize.value = size;
}

/** 筛选没有结果时：清掉关键词与类型 */
function handleClearFilters() {
  keyword.value = '';
  kindFilter.value = 'all';
}

watch(
  () => [filteredResources.value.length, keyword.value, kindFilter.value],
  () => {
    pageNo.value = 1;
  },
);
</script>

<template>
  <div class="resource-workspace">
    <div class="resource-toolbar">
      <div class="resource-toolbar__filters">
        <Input
          v-model:value="keyword"
          allow-clear
          class="resource-toolbar__search"
          placeholder="搜索资源名称"
        />
        <Select
          v-model:value="kindFilter"
          :options="kindOptions"
          class="resource-toolbar__kind"
        />
      </div>
      <div class="resource-toolbar__actions">
        <span class="resource-toolbar__summary">{{ toolbarSummary }}</span>
        <Button v-if="editable" type="primary" @click="handleOpenUploadModal">
          上传资源
        </Button>
      </div>
    </div>

    <ul v-if="filteredResources.length > 0" class="resource-list">
      <li
        v-for="resource in pagedResources"
        :key="resource.id || `${resource.resourceName}-${resource.fileUrl}`"
        class="resource-row"
      >
        <span aria-hidden="true" class="resource-row__icon">
          <IconifyIcon
            :icon="getResourceKindIcon(resolveResourceKind(resource))"
            class="size-4"
          />
        </span>
        <div class="resource-row__main">
          <div :title="resource.resourceName" class="resource-row__name">
            {{ resource.resourceName || '未命名资源' }}
          </div>
          <div class="resource-row__meta">
            <span>{{
              getResourceKindLabel(resolveResourceKind(resource))
            }}</span>
            <span>{{ formatResourceSize(resource.fileSize) }}</span>
            <span v-if="resource.createTime">{{ resource.createTime }}</span>
          </div>
        </div>
        <div class="resource-row__actions">
          <Button
            size="small"
            type="link"
            @click="handlePrimaryAction(resource)"
          >
            {{ getActionLabel(resource) }}
          </Button>
          <Button size="small" type="link" @click="handleDownload(resource)">
            下载
          </Button>
          <Button
            v-if="editable && resource.id"
            danger
            size="small"
            type="link"
            @click="
              handleDelete(resource as EduCourseResourceApi.CourseResource)
            "
          >
            删除
          </Button>
        </div>
      </li>
    </ul>

    <div v-if="filteredResources.length > 0" class="resource-pager">
      <Pagination
        :current="pageNo"
        :hide-on-single-page="false"
        :page-size="pageSize"
        :show-size-changer="true"
        :show-total="(value) => `共 ${value} 个资源`"
        :total="filteredResources.length"
        @change="handlePageChange"
      />
    </div>

    <EmptyState
      v-else-if="props.resources.length > 0"
      description="换个关键词或类型试试。"
      kind="filtered"
      title="没有符合条件的资源"
    >
      <template #action>
        <Button @click="handleClearFilters">清除筛选</Button>
      </template>
    </EmptyState>
    <EmptyState
      v-else
      :description="
        editable
          ? '点「上传资源」添加课件、视频或文档，学生可以在线查看和下载。'
          : '老师上传资源后会显示在这里。'
      "
      :title="emptyDescription"
    />

    <ResourcePreviewModal
      v-model:open="previewOpen"
      :download-resource="downloadResource"
      :fetch-resource="fetchResource"
      :resource="previewResource"
    />

    <Modal
      v-model:open="uploadModalOpen"
      :footer="null"
      destroy-on-close
      :width="640"
      title="上传资源"
    >
      <div class="resource-upload">
        <p class="resource-upload__intro">
          上传后会自动生成资源条目，Word、PDF、PPT
          和音视频文件可以直接在线预览。
        </p>

        <Upload.Dragger
          :accept="COURSE_RESOURCE_ACCEPT"
          :before-upload="handleBeforeUpload as UploadProps['beforeUpload']"
          :custom-request="handleCustomRequest"
          :disabled="!courseId"
          :multiple="true"
          :show-upload-list="false"
        >
          <div class="resource-dragger">
            <IconifyIcon
              aria-hidden="true"
              class="resource-dragger__icon"
              icon="lucide:cloud-upload"
            />
            <p class="resource-dragger__title">
              把文件拖到这里，或点击选择文件
            </p>
            <p class="resource-dragger__hint">
              支持视频、音频、图片、PDF、Word、PPT、Excel、文本和压缩包；可以一次选多个文件，单个文件不超过
              500 MB。
            </p>
          </div>
        </Upload.Dragger>

        <ul v-if="uploadTasks.length > 0" class="resource-upload-queue">
          <li
            v-for="task in uploadTasks.slice(0, 6)"
            :key="task.uid"
            class="resource-upload-queue__item"
          >
            <div class="resource-upload-queue__meta">
              <span :title="task.fileName" class="resource-upload-queue__name">
                {{ task.fileName }}
              </span>
              <StatusTag v-bind="getResourceUploadStatusMeta(task)" />
            </div>
            <Progress
              v-if="task.status === 'uploading'"
              :percent="task.percent"
              :show-info="false"
              size="small"
              status="active"
            />
            <p
              v-else-if="task.status === 'error'"
              class="resource-upload-queue__error"
            >
              {{ task.error || '网络或服务异常' }} · 可以重新选择这个文件上传
            </p>
          </li>
        </ul>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.resource-workspace {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
}

.resource-toolbar {
  /* 工具条：左边筛选，右边数量说明与主操作 */
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
}

.resource-toolbar__filters,
.resource-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
}

.resource-toolbar__search {
  width: 240px;
  max-width: 100%;
}

.resource-toolbar__kind {
  width: 140px;
}

.resource-toolbar__summary {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.resource-list {
  /* 资源列表：像表格一样用一圈淡边框加行分隔线，不再一条一张卡片 */
  padding: 0;
  margin: 0;
  overflow: hidden;
  list-style: none;
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.resource-row {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  min-height: 56px;
  padding: var(--nus-space-2) var(--nus-space-4);
  transition: var(--nus-transition-colors);
}

.resource-row + .resource-row {
  border-top: 1px solid var(--nus-border-subtle);
}

.resource-row:hover {
  background: var(--nus-bg-hover);
}

.resource-row__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--nus-text-secondary);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.resource-row__main {
  flex: 1;
  min-width: 0;
}

.resource-row__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: nowrap;
}

.resource-row__meta {
  display: flex;
  flex-wrap: wrap;
  margin-top: 2px;
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.resource-row__meta > span + span::before {
  margin: 0 6px;
  content: '·';
}

.resource-row__actions {
  display: flex;
  flex: none;
  gap: var(--nus-space-1);
  align-items: center;
}

.resource-pager {
  display: flex;
  justify-content: flex-end;
}

.resource-upload {
  /* 上传对话框 */
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
}

.resource-upload__intro {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.resource-dragger {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--nus-space-6) var(--nus-space-4);
}

.resource-dragger__icon {
  width: 24px;
  height: 24px;
  color: var(--nus-text-tertiary);
}

.resource-dragger__title {
  margin: var(--nus-space-3) 0 0;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.resource-dragger__hint {
  max-width: 440px;
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.resource-upload-queue {
  padding: 0;
  margin: 0;
  list-style: none;
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.resource-upload-queue__item {
  padding: var(--nus-space-3) var(--nus-space-4);
}

.resource-upload-queue__item + .resource-upload-queue__item {
  border-top: 1px solid var(--nus-border-subtle);
}

.resource-upload-queue__meta {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
}

.resource-upload-queue__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
  white-space: nowrap;
}

.resource-upload-queue__error {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

@media (width <= 768px) {
  .resource-row {
    flex-wrap: wrap;
  }

  .resource-row__actions {
    width: 100%;
    padding-left: 44px;
  }

  .resource-toolbar__search {
    width: 100%;
  }
}
</style>
