<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraFileConfigApi } from '#/api/infra/file-config';
import type { PopConfirmCopy } from '#/utils/confirm-copy';

import { ref } from 'vue';

import { confirm, Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { isEmpty, openWindow } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteFileConfig,
  deleteFileConfigList,
  getFileConfigPage,
  testFileConfig,
  updateFileConfigMaster,
} from '#/api/infra/file-config';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';
import {
  CONFIRM_CANCEL_TEXT,
  deleteBatchConfirm,
  deleteRowConfirm,
} from '#/utils/confirm-copy';

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

/** 创建文件配置 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑文件配置 */
function handleEdit(row: InfraFileConfigApi.FileConfig) {
  formModalApi.setData(row).open();
}

/** 设为主配置的确认文案：标题写对象和动作，说明写后果和怎么改回 */
function masterConfirm(row: InfraFileConfigApi.FileConfig): PopConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    description:
      '之后新上传的文件都存到这里；需要时可以再把别的配置设为主配置。',
    okText: '设为主配置',
    title: `把「${row.name}」设为主配置？`,
  };
}

/** 设为主配置 */
async function handleMaster(row: InfraFileConfigApi.FileConfig) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.updating', [row.name]),
    duration: 0,
  });
  try {
    await updateFileConfigMaster(row.id!);
    message.success($t('ui.actionMessage.updateSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 测试文件配置 */
async function handleTest(row: InfraFileConfigApi.FileConfig) {
  const hideLoading = message.loading({
    content: `正在用「${row.name}」上传测试文件…`,
    duration: 0,
  });
  try {
    const response = await testFileConfig(row.id!);
    // 确认是否访问该文件
    confirm({
      title: '测试上传成功',
      content: '测试文件已经上传，可以打开它确认能否正常访问。',
      confirmText: '打开文件',
      cancelText: '关闭',
      icon: 'success',
    }).then(() => {
      openWindow(response);
    });
  } finally {
    hideLoading();
  }
}

/** 删除文件配置 */
async function handleDelete(row: InfraFileConfigApi.FileConfig) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
  });
  try {
    await deleteFileConfig(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除文件配置 */
async function handleDeleteBatch() {
  await confirm({
    ...deleteBatchConfirm(
      '文件配置',
      checkedIds.value.length,
      '删除后不能恢复，已上传的文件不受影响。',
    ),
    icon: 'warning',
  });
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteFileConfigList(checkedIds.value);
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
  records: InfraFileConfigApi.FileConfig[];
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
          return await getFileConfigPage({
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
  } as VxeTableGridOptions<InfraFileConfigApi.FileConfig>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page
    auto-content-height
    description="设置文件存储方式；新上传的文件存到主配置指定的位置。"
    title="文件配置"
  >
    <FormModal @success="handleRefresh" />
    <Grid table-title="文件配置列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['文件配置']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['infra:file-config:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['infra:file-config:delete'],
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['infra:file-config:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '测试',
              type: 'link',
              icon: 'lucide:test-tube-diagonal',
              auth: ['infra:file-config:update'],
              onClick: handleTest.bind(null, row),
            },
            {
              label: '设为主配置',
              type: 'link',
              icon: 'lucide:star',
              auth: ['infra:file-config:update'],
              disabled: row.master,
              popConfirm: {
                ...masterConfirm(row),
                confirm: handleMaster.bind(null, row),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['infra:file-config:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '文件配置',
                  row.name,
                  '删除后不能恢复，已上传的文件不受影响。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取文件配置…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的文件配置"
        />
        <EmptyState
          v-else
          compact
          description="新建一个存储配置并设为主配置后，才能上传文件。"
          title="还没有文件配置"
        />
      </template>
    </Grid>
  </Page>
</template>
