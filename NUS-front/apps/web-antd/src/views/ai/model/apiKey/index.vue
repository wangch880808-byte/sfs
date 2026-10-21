<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiModelApiKeyApi } from '#/api/ai/model/apiKey';

import { ref } from 'vue';

import { Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteApiKey, getApiKeyPage } from '#/api/ai/model/apiKey';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';
import { deleteRowConfirm } from '#/utils/confirm-copy';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建 API 密钥 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑 API 密钥 */
function handleEdit(row: AiModelApiKeyApi.ApiKey) {
  formModalApi.setData(row).open();
}

/** 删除 API 密钥 */
async function handleDelete(row: AiModelApiKeyApi.ApiKey) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteApiKey(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
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
          return await getApiKeyPage({
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
  } as VxeTableGridOptions<AiModelApiKeyApi.ApiKey>,
});
</script>

<template>
  <Page
    auto-content-height
    description="保存各模型平台的访问密钥；模型配置从这里选择要用的密钥。"
    title="API 密钥"
  >
    <FormModal @success="handleRefresh" />
    <Grid table-title="API 密钥列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['密钥']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['ai:api-key:create'],
              onClick: handleCreate,
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
              auth: ['ai:api-key:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['ai:api-key:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '密钥',
                  row.name,
                  '使用它的模型会无法调用，删除后不能恢复。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取 API 密钥…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的 API 密钥"
        />
        <EmptyState
          v-else
          compact
          description="新建模型平台的密钥后，才能在模型配置里使用。"
          title="还没有 API 密钥"
        />
      </template>
    </Grid>
  </Page>
</template>
