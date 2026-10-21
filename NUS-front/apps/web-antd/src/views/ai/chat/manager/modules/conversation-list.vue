<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiChatConversationApi } from '#/api/ai/chat/conversation';

import { ref } from 'vue';

import { VbenLoading } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteChatConversationByAdmin,
  getChatConversationPage,
} from '#/api/ai/chat/conversation';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';
import { deleteRowConfirm } from '#/utils/confirm-copy';

import {
  useGridColumnsConversation,
  useGridFormSchemaConversation,
} from '../data';

/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除对话 */
async function handleDelete(row: AiChatConversationApi.ChatConversation) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.id]),
    duration: 0,
  });
  try {
    await deleteChatConversationByAdmin(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.id]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchemaConversation(),
  },
  gridOptions: {
    columns: useGridColumnsConversation(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          isFiltered.value = Object.values(formValues ?? {}).some(
            (value) => !isEmpty(value),
          );
          return await getChatConversationPage({
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
  } as VxeTableGridOptions<AiChatConversationApi.ChatConversation>,
});
</script>

<template>
  <div class="h-full">
    <Grid table-title="对话列表">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['ai:chat-conversation:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '对话',
                  row.title || '未命名对话',
                  '对话里的消息会一起删除，删除后不能恢复。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取对话…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的对话"
        />
        <EmptyState
          v-else
          compact
          description="用户在 AI 对话里新建的对话会出现在这里。"
          title="还没有对话"
        />
      </template>
    </Grid>
  </div>
</template>
