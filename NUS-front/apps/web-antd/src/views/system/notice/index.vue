<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemNoticeApi } from '#/api/system/notice';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteNotice,
  deleteNoticeList,
  getNoticePage,
  toggleNoticeTop,
} from '#/api/system/notice';
import { $t } from '#/locales';
import {
  deleteBatchConfirm,
  deleteRowConfirm,
  hasActiveFilters,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建公告 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑公告 */
function handleEdit(row: SystemNoticeApi.Notice) {
  formModalApi.setData(row).open();
}

/** 删除公告 */
async function handleDelete(row: SystemNoticeApi.Notice) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.title]),
    duration: 0,
  });
  try {
    await deleteNotice(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.title]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除公告 */
async function handleDeleteBatch() {
  await confirm(deleteBatchConfirm('公告', checkedIds.value.length));
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteNoticeList(checkedIds.value);
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
  records: SystemNoticeApi.Notice[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

/** 置顶/取消置顶 */
async function handleToggleTop(row: SystemNoticeApi.Notice) {
  // 失败时全局请求已提示原因，这里不再重复弹提示
  await toggleNoticeTop(row.id!);
  message.success(row.isTop === 1 ? '已取消置顶' : '已置顶');
  handleRefresh();
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
          return await getNoticePage({
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
  } as VxeTableGridOptions<SystemNoticeApi.Notice>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="公告列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['公告']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['system:notice:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:notice:delete'],
              disabled: isEmpty(checkedIds),
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #isTop="{ row }">
        <span v-if="row.isTop === 1" class="notice-top">
          <IconifyIcon class="notice-top__icon" icon="lucide:pin" />
          置顶
        </span>
        <span v-else class="notice-muted">—</span>
      </template>
      <template #empty>
        <GridEmpty
          :filtered="filtered"
          hint="新建公告后，可以在这里置顶或修改。"
          subject="公告"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['system:notice:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: row.isTop === 1 ? '取消置顶' : '置顶',
              type: 'link',
              auth: ['system:notice:update'],
              onClick: handleToggleTop.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:notice:delete'],
              popConfirm: {
                ...deleteRowConfirm('公告', row.title),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.notice-top {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
}

.notice-top__icon {
  width: 14px;
  height: 14px;
  color: var(--nus-text-secondary);
}

.notice-muted {
  color: var(--nus-text-tertiary);
}
</style>
