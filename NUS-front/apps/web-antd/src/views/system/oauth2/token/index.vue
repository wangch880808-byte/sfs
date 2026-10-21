<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemOAuth2TokenApi } from '#/api/system/oauth2/token';

import { ref } from 'vue';

import { confirm, Page } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteOAuth2Token,
  getOAuth2TokenPage,
} from '#/api/system/oauth2/token';
import { $t } from '#/locales';
import {
  deleteBatchConfirm,
  deleteRowConfirm,
  hasActiveFilters,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 删除令牌的后果 */
const TOKEN_DELETE_HINT = '删除后令牌立即失效，对应用户需要重新登录。';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除 OAuth2 令牌 */
async function handleDelete(row: SystemOAuth2TokenApi.OAuth2Token) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', ['令牌']),
    duration: 0,
  });
  try {
    await deleteOAuth2Token(row.accessToken);
    message.success($t('ui.actionMessage.deleteSuccess', ['令牌']));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除 OAuth2 令牌 */
async function handleDeleteBatch() {
  await confirm(
    deleteBatchConfirm('令牌', checkedIds.value.length, TOKEN_DELETE_HINT),
  );
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteOAuth2Token(checkedIds.value.join(','));
    checkedIds.value = [];
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<string[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: SystemOAuth2TokenApi.OAuth2Token[];
}) {
  checkedIds.value = records.map((item) => item.accessToken);
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
          filtered.value = hasActiveFilters(formValues);
          return await getOAuth2TokenPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'accessToken',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<SystemOAuth2TokenApi.OAuth2Token>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="令牌列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:oauth2-token:delete'],
              disabled: isEmpty(checkedIds),
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #empty>
        <GridEmpty
          :filtered="filtered"
          empty-title="没有在线令牌"
          hint="用户登录后会生成令牌，显示在这里。"
          subject="令牌"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:oauth2-token:delete'],
              popConfirm: {
                ...deleteRowConfirm('令牌', undefined, TOKEN_DELETE_HINT),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
