<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraFileApi } from '#/api/infra/file';

import { ref } from 'vue';

import { confirm, Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { isEmpty, openWindow } from '@vben/utils';

import { useClipboard } from '@vueuse/core';
import { Button, Image, message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteFile, deleteFileList, getFilePage } from '#/api/infra/file';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';
import { deleteBatchConfirm, deleteRowConfirm } from '#/utils/confirm-copy';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 上传文件 */
function handleUpload() {
  formModalApi.setData(null).open();
}

/** 复制链接到剪贴板 */
const { copy } = useClipboard({ legacy: true });
async function handleCopyUrl(row: InfraFileApi.File) {
  if (!row.url) {
    message.error('这个文件没有访问链接，不能复制');
    return;
  }

  try {
    await copy(row.url);
    message.success('已复制文件链接');
  } catch {
    message.error('链接没有复制成功：浏览器没有允许访问剪贴板。');
  }
}

/** 删除文件 */
async function handleDelete(row: InfraFileApi.File) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name || row.path]),
    duration: 0,
  });
  try {
    await deleteFile(row.id!);
    message.success(
      $t('ui.actionMessage.deleteSuccess', [row.name || row.path]),
    );
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除文件 */
async function handleDeleteBatch() {
  await confirm({
    ...deleteBatchConfirm(
      '文件',
      checkedIds.value.length,
      '删除后不能恢复，引用这些链接的地方会无法打开。',
    ),
    icon: 'warning',
  });
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteFileList(checkedIds.value);
    checkedIds.value = [];
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);
function handleRowCheckboxChange({
  records,
}: {
  records: InfraFileApi.File[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          isFiltered.value = Object.values(formValues ?? {}).some(
            (value) => !isEmpty(value),
          );
          return await getFilePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<InfraFileApi.File>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page
    auto-content-height
    description="查看已上传到文件存储的文件，可以预览、复制链接或删除。"
    title="文件列表"
  >
    <FormModal @success="handleRefresh" />
    <Grid table-title="文件列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '上传图片',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              onClick: handleUpload,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['infra:file:delete'],
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #file-content="{ row }">
        <Image
          v-if="row.type && row.type.includes('image')"
          :src="row.url"
          class="file-thumb"
        />
        <Button
          v-else
          size="small"
          type="link"
          @click="() => openWindow(row.url!)"
        >
          {{ row.type && row.type.includes('pdf') ? '预览' : '下载' }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '复制链接',
              type: 'link',
              icon: ACTION_ICON.COPY,
              onClick: handleCopyUrl.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['infra:file:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '文件',
                  row.name || row.path,
                  '删除后不能恢复，引用这个链接的地方会无法打开。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取文件…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的文件"
        />
        <EmptyState
          v-else
          compact
          description="上传的文件会出现在这里，可以复制链接在别处使用。"
          title="还没有文件"
        />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
/* 图片缩略图限制在行高之内，圆角取标签档 */
.file-thumb :deep(img),
:deep(.file-thumb.ant-image-img) {
  max-height: 32px;
  object-fit: cover;
  border-radius: var(--nus-radius-sm);
}
</style>
