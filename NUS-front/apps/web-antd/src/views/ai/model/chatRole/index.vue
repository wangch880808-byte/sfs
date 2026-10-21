<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiModelChatRoleApi } from '#/api/ai/model/chatRole';

import { ref } from 'vue';

import { Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteChatRole, getChatRolePage } from '#/api/ai/model/chatRole';
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

/** 创建聊天角色 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑聊天角色 */
function handleEdit(row: AiModelChatRoleApi.ChatRole) {
  formModalApi.setData({ formType: 'update', ...row }).open();
}

/** 删除聊天角色 */
async function handleDelete(row: AiModelChatRoleApi.ChatRole) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteChatRole(row.id!);
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
          return await getChatRolePage({
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
  } as VxeTableGridOptions<AiModelChatRoleApi.ChatRole>,
});
</script>

<template>
  <Page
    auto-content-height
    description="设置 AI 对话可用的角色：角色设定、绑定的模型与是否公开。"
    title="聊天角色"
  >
    <FormModal @success="handleRefresh" />
    <Grid table-title="聊天角色列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['聊天角色']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['ai:chat-role:create'],
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
              auth: ['ai:chat-role:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['ai:chat-role:delete'],
              popConfirm: {
                ...deleteRowConfirm('角色', row.name, '删除后不能恢复。'),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取聊天角色…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的聊天角色"
        />
        <EmptyState
          v-else
          compact
          description="新建角色并设为公开后，用户可以在 AI 对话的角色仓库里使用。"
          title="还没有聊天角色"
        />
      </template>
    </Grid>
  </Page>
</template>
