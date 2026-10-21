<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemSocialClientApi } from '#/api/system/social/client';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSocialClient,
  deleteSocialClientList,
  getSocialClientPage,
} from '#/api/system/social/client';
import { StatusTag } from '#/components/status-tag';
import { $t } from '#/locales';
import {
  deleteBatchConfirm,
  deleteRowConfirm,
  hasActiveFilters,
} from '#/utils/confirm-copy';
import { getSecretConfiguredMeta } from '#/utils/status-admin';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 删除社交客户端的后果 */
const SOCIAL_CLIENT_DELETE_HINT =
  '删除后不能恢复，用户不能再用相应平台的账号登录。';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建社交客户端 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑社交客户端 */
function handleEdit(row: SystemSocialClientApi.SocialClient) {
  formModalApi.setData(row).open();
}

/** 删除社交客户端 */
async function handleDelete(row: SystemSocialClientApi.SocialClient) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteSocialClient(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除社交客户端 */
async function handleDeleteBatch() {
  await confirm(
    deleteBatchConfirm(
      '社交客户端',
      checkedIds.value.length,
      SOCIAL_CLIENT_DELETE_HINT,
    ),
  );
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteSocialClientList(checkedIds.value);
    checkedIds.value = [];
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: SystemSocialClientApi.SocialClient[];
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
          filtered.value = hasActiveFilters(formValues);
          return await getSocialClientPage({
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
  } as VxeTableGridOptions<SystemSocialClientApi.SocialClient>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="社交客户端列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['社交客户端']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['system:social-client:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:social-client:delete'],
              disabled: isEmpty(checkedIds),
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #clientSecretConfigured="{ row }">
        <StatusTag
          v-bind="getSecretConfiguredMeta(row.clientSecretConfigured)"
        />
      </template>
      <template #empty>
        <GridEmpty
          :filtered="filtered"
          hint="新建客户端并配置好密钥后，用户可以用对应平台的账号登录。"
          subject="社交客户端"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['system:social-client:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:social-client:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '社交客户端',
                  row.name,
                  SOCIAL_CLIENT_DELETE_HINT,
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
