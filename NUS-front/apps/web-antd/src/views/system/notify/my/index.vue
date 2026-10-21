<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getMyNotifyMessagePage,
  updateAllNotifyMessageRead,
  updateNotifyMessageRead,
} from '#/api/system/notify/message';
import { StatusTag } from '#/components/status-tag';
import { hasActiveFilters } from '#/utils/confirm-copy';
import { emitNotifyUnreadChanged } from '#/utils/notify-unread';
import { getNotifyReadMeta } from '#/utils/status-admin';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 查看通知详情 */
function handleDetail(row: SystemNotifyMessageApi.NotifyMessage) {
  detailModalApi.setData(row).open();
}

/** 标记一条通知已读 */
async function handleRead(row: SystemNotifyMessageApi.NotifyMessage) {
  const hideLoading = message.loading({
    content: '正在标记已读…',
    duration: 0,
  });
  try {
    await updateNotifyMessageRead([row.id]);
    // 顶栏铃铛立即重新读取未读数
    emitNotifyUnreadChanged();
    message.success('已标记为已读');
    handleRefresh();
    // 打开详情
    handleDetail(row);
  } finally {
    hideLoading();
  }
}

/** 标记选中的通知为已读 */
async function handleMarkRead() {
  const rows = gridApi.grid.getCheckboxRecords();
  if (!rows || rows.length === 0) {
    message.warning('先勾选要标记的通知');
    return;
  }

  const ids = rows.map((row: SystemNotifyMessageApi.NotifyMessage) => row.id);
  const hideLoading = message.loading({
    content: '正在标记已读…',
    duration: 0,
  });
  try {
    await updateNotifyMessageRead(ids);
    emitNotifyUnreadChanged();
    checkedIds.value = [];
    message.success('已标记为已读');
    await gridApi.grid.setAllCheckboxRow(false);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: SystemNotifyMessageApi.NotifyMessage[];
}) {
  checkedIds.value = records.map((item) => item.id);
}

/** 标记所有通知为已读 */
async function handleMarkAllRead() {
  const hideLoading = message.loading({
    content: '正在把全部通知标记为已读…',
    duration: 0,
  });
  try {
    await updateAllNotifyMessageRead();
    emitNotifyUnreadChanged();
    message.success('已全部标记为已读');
    checkedIds.value = [];
    await gridApi.grid.setAllCheckboxRow(false);
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
          filtered.value = hasActiveFilters(formValues);
          return await getMyNotifyMessagePage({
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
    checkboxConfig: {
      checkMethod: (params: { row: SystemNotifyMessageApi.NotifyMessage }) =>
        !params.row.readStatus,
      highlight: true,
    },
  } as VxeTableGridOptions<SystemNotifyMessageApi.NotifyMessage>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>
<template>
  <Page auto-content-height>
    <DetailModal @success="handleRefresh" />
    <Grid table-title="我的通知">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '标记已读',
              type: 'default',
              icon: 'lucide:check',
              disabled: isEmpty(checkedIds),
              onClick: handleMarkRead,
            },
            {
              label: '全部标记已读',
              type: 'default',
              icon: 'lucide:check-check',
              onClick: handleMarkAllRead,
            },
          ]"
        />
      </template>
      <template #readStatus="{ row }">
        <StatusTag v-bind="getNotifyReadMeta(row.readStatus)" />
      </template>
      <template #empty>
        <GridEmpty
          :filtered="filtered"
          hint="系统发给你的通知会显示在这里。"
          subject="通知"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看',
              type: 'link',
              ifShow: row.readStatus,
              icon: ACTION_ICON.VIEW,
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '查看',
              type: 'link',
              ifShow: !row.readStatus,
              icon: ACTION_ICON.VIEW,
              onClick: handleRead.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
