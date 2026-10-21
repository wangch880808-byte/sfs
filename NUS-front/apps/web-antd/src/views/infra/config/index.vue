<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraConfigApi } from '#/api/infra/config';

import { ref } from 'vue';

import { confirm, Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteConfig,
  deleteConfigList,
  exportConfig,
  getConfigPage,
} from '#/api/infra/config';
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

/** 导出表格 */
async function handleExport() {
  const data = await exportConfig(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '参数配置.xls', source: data });
}

/** 创建参数 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑参数 */
function handleEdit(row: InfraConfigApi.Config) {
  formModalApi.setData(row).open();
}

/** 删除参数 */
async function handleDelete(row: InfraConfigApi.Config) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteConfig(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除参数 */
async function handleDeleteBatch() {
  await confirm({
    ...deleteBatchConfirm(
      '参数',
      checkedIds.value.length,
      '删除后不能恢复，读取这些键名的功能会改用默认值。',
    ),
    icon: 'warning',
  });
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteConfigList(checkedIds.value);
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
  records: InfraConfigApi.Config[];
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
          return await getConfigPage({
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
  } as VxeTableGridOptions<InfraConfigApi.Config>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page
    auto-content-height
    description="维护系统参数的键名与取值，业务功能按键名读取。"
    title="配置管理"
  >
    <FormModal @success="handleRefresh" />
    <Grid table-title="参数列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['参数']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['infra:config:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['infra:config:export'],
              onClick: handleExport,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['infra:config:delete'],
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
              auth: ['infra:config:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['infra:config:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '参数',
                  row.name,
                  '删除后不能恢复，读取这个键名的功能会改用默认值。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取参数…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的参数"
        />
        <EmptyState
          v-else
          compact
          description="新建参数后，业务功能会按键名读取它的取值。"
          title="还没有参数"
        />
      </template>
    </Grid>
  </Page>
</template>
