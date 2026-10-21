<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiModelToolApi } from '#/api/ai/model/tool';

import { ref } from 'vue';

import { Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteTool, getToolPage } from '#/api/ai/model/tool';
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

/** 创建工具 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑工具 */
function handleEdit(row: AiModelToolApi.Tool) {
  formModalApi.setData(row).open();
}

/** 删除工具 */
async function handleDelete(row: AiModelToolApi.Tool) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteTool(row.id!);
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
          return await getToolPage({
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
  } as VxeTableGridOptions<AiModelToolApi.Tool>,
});
</script>

<template>
  <Page
    auto-content-height
    description="管理 AI 对话可以调用的工具，在聊天角色里选择启用。"
    title="工具管理"
  >
    <FormModal @success="handleRefresh" />
    <Grid table-title="工具列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['工具']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['ai:tool:create'],
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
              auth: ['ai:tool:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['ai:tool:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '工具',
                  row.name,
                  '用到它的角色将不能再调用，删除后不能恢复。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取工具…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的工具"
        />
        <EmptyState
          v-else
          compact
          description="新建工具后，可以在聊天角色里选择使用。"
          title="还没有工具"
        />
      </template>
    </Grid>
  </Page>
</template>
